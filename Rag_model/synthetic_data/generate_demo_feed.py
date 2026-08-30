"""Seeds Blue DB with a realistic demo dataset so the Feature Map + Alerts endpoints
(api/routes/dashboard.py) have real data to serve before genuine device traffic exists. Run once
against the already-configured POSTGRES_DSN:

    python -m synthetic_data.generate_demo_feed

This is an ORCHESTRATOR over the existing ingestion pipeline, not a reimplementation of it:
  1. ingestion.enrich_blue_db_metadata  -- real spam text corpus (train.csv + synthetic_dataset.csv,
     ~7000 spam-labeled rows) + synthesized region/persona/message_type/timestamp metadata.
  2. ingestion.run_clustering           -- real DenStream micro-clustering + DBSCAN macro-clustering
     over that enriched data (text embedding + region/persona/message_type weighted dims).
  3. db.seed.seed_blue_db               -- bulk-upserts the clustered output into Postgres.
This alone produces organic-looking messages/clusters. Two things it does NOT produce, added here:
  - `national_digest` rows: nothing seeds this table today outside the live cybercrime.gov.in
    scraper (crawlers/cybercrime_digest_job.py). We stand in with synthetic rows dated "today" so
    the digest-sourced half of GET /v1/alerts/daily and GET /v1/heatmap has data to show. Swapping
    to the real scraper later needs no code change -- same table, same columns.
  - A GUARANTEED threshold-crossing alert: the enriched dataset's timestamps are triangularly
    biased toward "now" but not concentrated enough to reliably put >=20 messages in the same
    macro-cluster's last-24h window on every run. We top up the 3 heaviest macro-clusters with a
    burst of recent messages so alerting.daily_job.macro_clusters_crossing_threshold (and therefore
    GET /v1/alerts/daily's threshold branch) always has something real to show for a demo.
"""

import random
import uuid
from datetime import datetime, timedelta, timezone

from sqlalchemy import func

from clustering.region_weighting import REGIONS
from config.settings import settings
from db.postgres_client import Cluster, Message, NationalDigest, get_session_factory

DIGEST_FRAUD_TYPES = [
    "Fake banking app",
    "KYC update scam",
    "OTP phishing",
    "Lottery/prize scam",
    "Job offer scam",
    "Loan app fraud",
]

THRESHOLD_TOPUP_MACRO_COUNT = 3
THRESHOLD_TOPUP_MESSAGES_PER_MACRO = 25


def _run_ingestion_pipeline() -> None:
    from ingestion import enrich_blue_db_metadata, run_clustering
    from db.seed import seed_blue_db

    print("[1/3] Enriching real spam-text corpus with region/persona/message_type/timestamp...")
    enrich_blue_db_metadata.main()

    print("[2/3] Running DenStream + DBSCAN clustering over the enriched messages...")
    run_clustering.run()

    print("[3/3] Seeding Blue DB (Postgres) with the clustered output...")
    seed_blue_db.seed()


def _seed_national_digest(session) -> int:
    """Synthetic stand-in for crawlers/cybercrime_digest_job.py's live scrape -- same table,
    dated today, so the digest side of the heatmap/alerts endpoints has data before the real
    scraper has run against the live site."""
    today = datetime.now(timezone.utc)
    rows = []
    for region, (_, _, weight) in REGIONS.items():
        if region == "Unknown":
            continue
        # Report count scales with the region's existing sampling weight (Mumbai/Delhi busier),
        # plus noise, so the digest isn't a flat number across every region.
        base = max(1, int(weight * 400))
        for fraud_type in random.sample(DIGEST_FRAUD_TYPES, k=random.randint(1, 3)):
            rows.append(
                NationalDigest(
                    date=today,
                    region=region,
                    fraud_type=fraud_type,
                    report_count=max(1, base + random.randint(-base // 2, base // 2)),
                    source="cybercrime.gov.in (synthetic demo seed)",
                )
            )
    session.bulk_save_objects(rows)
    session.commit()
    return len(rows)


def _topup_threshold_alerts(session) -> int:
    """Guarantees a few macro-clusters visibly cross ALERT_THRESHOLD_MESSAGE_COUNT in the trailing
    24h, so the threshold-alert path in region_alerts.py always has something to show on a fresh
    seed regardless of how the organic DenStream/DBSCAN run happened to bucket messages."""
    heaviest_macros = (
        session.query(Cluster)
        .filter(Cluster.cluster_type == "macro")
        .order_by(Cluster.weight.desc())
        .limit(THRESHOLD_TOPUP_MACRO_COUNT)
        .all()
    )

    inserted = 0
    for macro in heaviest_macros:
        micro_child = (
            session.query(Cluster.cluster_id)
            .filter(Cluster.parent_macro_cluster_id == macro.cluster_id)
            .first()
        )
        if micro_child is None:
            continue
        micro_cluster_id = micro_child[0]

        template = session.query(Message).filter(Message.cluster_id == micro_cluster_id).first()
        if template is None:
            continue

        new_rows = []
        for _ in range(THRESHOLD_TOPUP_MESSAGES_PER_MACRO):
            new_rows.append(
                Message(
                    message_id=str(uuid.uuid4()),
                    original_message=template.original_message,
                    language=template.language,
                    message_english=template.message_english,
                    needs_translation=template.needs_translation,
                    channel=template.channel,
                    content_type=template.content_type,
                    region=template.region,
                    persona=template.persona,
                    message_type=template.message_type,
                    sender=template.sender,
                    cluster_id=micro_cluster_id,
                    timestamp=datetime.now(timezone.utc) - timedelta(hours=random.uniform(0, 6)),
                )
            )
        session.bulk_save_objects(new_rows)
        inserted += len(new_rows)

    session.commit()
    return inserted


def main() -> None:
    if not settings.postgres_dsn:
        raise RuntimeError("POSTGRES_DSN not set -- see .env.example")

    _run_ingestion_pipeline()

    Session = get_session_factory()
    with Session() as session:
        digest_count = _seed_national_digest(session)
        print(f"Seeded {digest_count} synthetic national_digest rows for today.")

        topup_count = _topup_threshold_alerts(session)
        print(f"Topped up {topup_count} recent messages across the heaviest macro-clusters "
              f"to guarantee visible threshold alerts.")

        total_messages = session.query(func.count(Message.message_id)).scalar()
        total_clusters = session.query(func.count(Cluster.cluster_id)).scalar()
        print(f"Done. Blue DB now has {total_messages} messages, {total_clusters} clusters.")


if __name__ == "__main__":
    main()
