"""Shared logic behind both the push path (alerting/daily_job.py -> FCM) and the pull path
(api/routes/dashboard.py -> GET /v1/alerts/daily, /v1/campaigns). Kept in one module so the two
paths can never drift into showing different alerts for the same underlying data.

Two independent, always-on alert sources feed the same region-filtered list. For THIS module's pull
path (GET /v1/alerts/daily), persona only changes the WORDING of the body text, never which alerts
appear -- any user polling a region sees every alert for that region, phrased for their own
persona. (The push path in daily_job.py is different since clustering became persona-aware:
run_threshold_alerts -> users_in_region_persona now also filters WHO receives a threshold push by
the triggering cluster's persona, not just region. That's a deliberate divergence, not a bug --
pull requests are user-initiated and always want a full picture of their region; pushes are
proactive and should target the audience the cluster is actually about.)
  1. threshold  -- a Blue DB macro-cluster whose message count crossed settings.alert_threshold_message_count
                    in the trailing 24h (see daily_job.macro_clusters_crossing_threshold).
  2. cybercrime.gov.in daily digest -- NationalDigest rows from the most recent scrape, regardless
                    of any threshold.
"""

from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from sqlalchemy import func
from sqlalchemy.orm import Session

from alerting.campaign_playbook import playbook_entry
from clustering.region_weighting import nearest_regions
from config.settings import settings
from db.postgres_client import Cluster, Message, NationalDigest

CAMPAIGN_WINDOW_DAYS = 7  # trailing window for "ongoing campaigns" (per spec: past 7 days data)

PERSONA_ALERT_TEMPLATES: dict[str, str] = {
    "general": "{count} {fraud_type} reports in {region} this week. Stay alert -- verify before "
    "you click a link or share any code.",
    "salaried_professional": "{count} {fraud_type} scams targeting working professionals reported "
    "in {region}. Double-check payroll/HR/bank messages before clicking any link.",
    "student": "{count} {fraud_type} scams reported near {region} -- fake job and prize offers are "
    "trending. Never pay a 'registration fee' or install an app to claim anything.",
    "senior_citizen": "Important safety notice: {count} {fraud_type} scams reported in {region} "
    "targeting senior citizens. Please do not share your OTP, PIN, or Aadhaar details with anyone "
    "who calls or messages you.",
    "business_owner": "{count} {fraud_type} scams reported in {region} targeting small businesses "
    "and traders. Verify any tax, GST, or vendor-payment message directly on the official portal.",
    "homemaker": "{count} {fraud_type} scams reported in {region}, often disguised as bank KYC or "
    "cash-prize messages. Please check with a family member before clicking any link or sharing "
    "bank details.",
}
DEFAULT_PERSONA_TEMPLATE = PERSONA_ALERT_TEMPLATES["general"]


def persona_alert_body(persona: str | None, fraud_type: str, count: int, region: str) -> str:
    template = PERSONA_ALERT_TEMPLATES.get(persona or "", DEFAULT_PERSONA_TEMPLATE)
    return template.format(count=count, fraud_type=fraud_type, region=region)


def _severity_from_count(count: int, medium_at: int, high_at: int) -> str:
    if count >= high_at:
        return "high"
    if count >= medium_at:
        return "medium"
    return "low"


@dataclass
class RegionalAlertEntry:
    id: str
    region: str
    is_nearby: bool
    source: str  # "threshold" | "cybercrime.gov.in"
    severity: str  # "low" | "medium" | "high"
    fraud_type: str  # human-readable label
    body: str  # persona-worded
    report_count: int
    timestamp: datetime


@dataclass
class CampaignEntry:
    region: str
    target_persona: str
    lure_label: str
    malicious_apk_theme: str
    intervention: str
    message_count: int


def _threshold_alerts_for_region(session: Session, region: str, persona: str | None) -> list[RegionalAlertEntry]:
    from alerting.daily_job import macro_clusters_crossing_threshold  # local import avoids a cycle

    entries = []
    for macro in macro_clusters_crossing_threshold(session):
        if macro.region != region:
            continue
        count = getattr(macro, "_recent_count", macro.weight)
        entry = playbook_entry(macro.fraud_type)
        entries.append(
            RegionalAlertEntry(
                id=f"threshold:{macro.cluster_id}",
                region=region,
                is_nearby=False,
                source="threshold",
                severity=_severity_from_count(
                    count,
                    medium_at=settings.alert_threshold_message_count,
                    high_at=settings.alert_threshold_message_count * 3,
                ),
                fraud_type=entry.lure_label,
                body=persona_alert_body(persona, entry.lure_label, count, region),
                report_count=count,
                timestamp=macro.updated_at,
            )
        )
    return entries


def _digest_alerts_for_region(
    session: Session, region: str, persona: str | None, is_nearby: bool
) -> list[RegionalAlertEntry]:
    latest_date = (
        session.query(func.max(NationalDigest.date))
        .filter(NationalDigest.region == region)
        .scalar()
    )
    if latest_date is None:
        return []
    rows = (
        session.query(NationalDigest)
        .filter(NationalDigest.region == region, NationalDigest.date == latest_date)
        .all()
    )
    entries = []
    for row in rows:
        fraud_label = row.fraud_type or "fraud"
        entries.append(
            RegionalAlertEntry(
                id=f"digest:{row.id}",
                region=region,
                is_nearby=is_nearby,
                source="cybercrime.gov.in",
                severity=_severity_from_count(row.report_count, medium_at=30, high_at=100),
                fraud_type=fraud_label,
                body=persona_alert_body(persona, fraud_label, row.report_count, region),
                report_count=row.report_count,
                timestamp=row.date,
            )
        )
    return entries


def build_daily_alerts(
    session: Session, region: str, persona: str | None = None, include_nearby: bool = True
) -> list[RegionalAlertEntry]:
    alerts = _threshold_alerts_for_region(session, region, persona)
    alerts += _digest_alerts_for_region(session, region, persona, is_nearby=False)

    if include_nearby:
        for nearby_region in nearest_regions(region, k=3):
            nearby_threshold = _threshold_alerts_for_region(session, nearby_region, persona)
            nearby_digest = _digest_alerts_for_region(session, nearby_region, persona, is_nearby=True)
            for entry in nearby_threshold:
                entry.is_nearby = True
            alerts += nearby_threshold + nearby_digest

    severity_rank = {"high": 0, "medium": 1, "low": 2}
    return sorted(alerts, key=lambda a: (a.is_nearby, severity_rank[a.severity], -a.report_count))


def active_campaigns_for_region(session: Session, region: str, window_days: int = CAMPAIGN_WINDOW_DAYS) -> list[CampaignEntry]:
    cutoff = datetime.now(timezone.utc) - timedelta(days=window_days)

    rows = (
        session.query(Message.message_type, Message.persona, func.count(Message.message_id))
        .filter(Message.region == region, Message.timestamp >= cutoff, Message.message_type.isnot(None))
        .group_by(Message.message_type, Message.persona)
        .all()
    )

    # Collapse to one row per message_type, picking the persona with the highest count as the
    # "target persona" for that campaign (a campaign can touch several personas; we surface the
    # one it's hitting hardest).
    best_by_type: dict[str, tuple[str, int]] = {}
    for message_type, persona, count in rows:
        current = best_by_type.get(message_type)
        if current is None or count > current[1]:
            best_by_type[message_type] = (persona or "general", count)

    campaigns = []
    for message_type, (target_persona, count) in best_by_type.items():
        entry = playbook_entry(message_type)
        campaigns.append(
            CampaignEntry(
                region=region,
                target_persona=target_persona,
                lure_label=entry.lure_label,
                malicious_apk_theme=entry.malicious_apk_theme,
                intervention=entry.intervention,
                message_count=count,
            )
        )
    return sorted(campaigns, key=lambda c: c.message_count, reverse=True)
