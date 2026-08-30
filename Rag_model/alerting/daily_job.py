"""The 07:00 IST daily job (GCP Cloud Scheduler -> Cloud Run Job, cron `30 1 * * *` UTC):
1. Threshold check: macro-clusters whose message count crossed the alert threshold in the last
   24h get an LLM-generated regional alert, FCM-pushed to users in the matching region
   (vulnerable-group users prioritized).
2. Heatmap: rebuilt from the trailing window (alerting/heatmap.py) and pushed as a daily digest.

Runs after crawlers/cybercrime_digest_job.py (scheduled ~06:00 IST) so same-day digest data is
available for the heatmap.
"""

from datetime import datetime, timedelta, timezone

from sqlalchemy import func
from sqlalchemy.orm import Session

from alerting.fcm_sender import send_to_tokens
from alerting.heatmap import build_heatmap
from config.settings import settings
from db.postgres_client import Cluster, Message, UserMap
from db.vector_store import retrieve_guideline_docs
from llm.groq_client import generate_threat_report
from llm.prompt_templates import build_alerting_prompt


def macro_clusters_crossing_threshold(session: Session) -> list[Cluster]:
    cutoff = datetime.now(timezone.utc) - timedelta(hours=24)

    counts = dict(
        session.query(Message.cluster_id, func.count(Message.message_id))
        .filter(Message.timestamp >= cutoff, Message.cluster_id.isnot(None))
        .group_by(Message.cluster_id)
        .all()
    )

    macro_clusters = session.query(Cluster).filter(Cluster.cluster_type == "macro").all()
    triggered = []
    for macro in macro_clusters:
        recent_count = sum(
            counts.get(micro_id, 0)
            for micro_id in _child_micro_ids(session, macro.cluster_id)
        )
        if recent_count >= settings.alert_threshold_message_count:
            macro._recent_count = recent_count  # transient attribute, not persisted
            triggered.append(macro)
    return triggered


def _child_micro_ids(session: Session, macro_id: str) -> list[str]:
    rows = session.query(Cluster.cluster_id).filter(Cluster.parent_macro_cluster_id == macro_id).all()
    return [r[0] for r in rows]


def users_in_region(session: Session, region: str) -> list[str]:
    """All FCM tokens for a region, regardless of persona -- used by the heatmap digest, which
    fans out by region/token only (see run_daily_heatmap_digest)."""
    rows = (
        session.query(UserMap.metadata_json)
        .filter(UserMap.region == region)
        .order_by(UserMap.is_vulnerable_group.desc())
        .all()
    )
    return [r[0].get("fcm_token") for r in rows if r[0] and r[0].get("fcm_token")]


def users_in_region_persona(session: Session, region: str, persona: str | None) -> list[str]:
    """FCM tokens for a region AND persona -- used by threshold alerts, since clusters are now a
    (region, message_type, persona) triple (see ingestion/run_clustering.py::build_feature_vector):
    a credit-card-spam cluster tagged "student" in Mumbai should alert Mumbai students, not every
    Mumbai user regardless of persona. Falls back to region-only if the cluster's majority-vote
    persona is unknown/mixed (`majority()` in run_clustering.py returns "unknown" for that case)."""
    query = session.query(UserMap.metadata_json).filter(UserMap.region == region)
    if persona and persona != "unknown":
        query = query.filter(UserMap.persona == persona)
    rows = query.order_by(UserMap.is_vulnerable_group.desc()).all()
    return [r[0].get("fcm_token") for r in rows if r[0] and r[0].get("fcm_token")]


def run_threshold_alerts(session: Session) -> int:
    sent = 0
    for macro in macro_clusters_crossing_threshold(session):
        prompt = build_alerting_prompt(
            macro_cluster_sample_message=macro.sample_message,
            fraud_type=macro.fraud_type or "unknown",
            region=macro.region or "unknown",
            message_count_last_24h=getattr(macro, "_recent_count", macro.weight),
            retrieved_docs=retrieve_guideline_docs(macro.sample_message),
        )
        report = generate_threat_report(prompt)
        tokens = users_in_region_persona(session, macro.region, macro.persona) if macro.region else []
        sent += send_to_tokens(
            tokens,
            title=f"Fraud alert: {report.threat_type}",
            body=report.plain_language_explanation,
            data={"risk_score": str(report.risk_score), "region": macro.region or "", "persona": macro.persona or ""},
        )
    return sent


def run_daily_heatmap_digest(session: Session) -> None:
    """Pushes a digest per top region. Body wording goes through the same
    alerting.region_alerts.persona_alert_body table the pull-side GET /v1/alerts/daily endpoint
    uses, so a user polling the API and a user who gets this push see consistently-worded alerts
    -- this job just doesn't know an individual recipient's persona (it fans out by region/token,
    not by request), so it uses the "general" tone."""
    from alerting.region_alerts import persona_alert_body

    heatmap = build_heatmap(session)
    for entry in heatmap[:10]:  # top 10 regions by volume get a region-specific digest push
        tokens = users_in_region(session, entry.region)
        send_to_tokens(
            tokens,
            title="Daily fraud activity update",
            body=persona_alert_body(None, "fraud", entry.total, entry.region),
            data={"region": entry.region, "total": str(entry.total)},
        )


def run(session: Session) -> None:
    alerts_sent = run_threshold_alerts(session)
    run_daily_heatmap_digest(session)
    print(f"Daily job complete: {alerts_sent} threshold alerts sent, heatmap digest pushed.")
