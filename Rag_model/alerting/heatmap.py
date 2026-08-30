"""Rebuilds the India spam-by-region heatmap from a trailing 10-15 day window, combining
cybercrime.gov.in's `national_digest` with Blue DB's own `messages` counts by region. Runs as
part of the 07:00 IST daily job (alerting/daily_job.py).
"""

from collections import defaultdict
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from sqlalchemy import func
from sqlalchemy.orm import Session

from db.postgres_client import Message, NationalDigest

WINDOW_DAYS = 12  # midpoint of the spec'd 10-15 day trailing window


@dataclass
class RegionHeatmapEntry:
    region: str
    blue_db_count: int
    national_digest_count: int
    total: int


def build_heatmap(session: Session, window_days: int = WINDOW_DAYS) -> list[RegionHeatmapEntry]:
    cutoff = datetime.now(timezone.utc) - timedelta(days=window_days)

    blue_db_counts = dict(
        session.query(Message.region, func.count(Message.message_id))
        .filter(Message.timestamp >= cutoff, Message.region.isnot(None))
        .group_by(Message.region)
        .all()
    )

    digest_counts = dict(
        session.query(NationalDigest.region, func.sum(NationalDigest.report_count))
        .filter(NationalDigest.date >= cutoff)
        .group_by(NationalDigest.region)
        .all()
    )

    all_regions = set(blue_db_counts) | set(digest_counts)
    entries = []
    for region in all_regions:
        blue = blue_db_counts.get(region, 0)
        digest = int(digest_counts.get(region, 0) or 0)
        entries.append(RegionHeatmapEntry(region=region, blue_db_count=blue, national_digest_count=digest, total=blue + digest))

    return sorted(entries, key=lambda e: e.total, reverse=True)
