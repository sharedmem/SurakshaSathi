"""Scheduled job (GCP Cloud Scheduler -> Cloud Run Job, cron `0 */12 * * *`): fetches the
OpenPhish free-tier feed, diffs against last-seen content hash to skip unchanged pulls, and
upserts new URLs into Postgres `threat_intel_urls` + the in-process Bloom filter refresh list.
"""

import hashlib
from datetime import datetime, timezone

import httpx

from common.resilience import with_retry
from db.postgres_client import ThreatIntelUrl

OPENPHISH_FEED_URL = "https://openphish.com/feed.txt"  # plain-text, one URL per line, free tier


@with_retry("openphish", exceptions=(httpx.HTTPError,))
def fetch_feed() -> list[str]:
    resp = httpx.get(OPENPHISH_FEED_URL, timeout=10)
    resp.raise_for_status()
    return [line.strip() for line in resp.text.splitlines() if line.strip()]


def url_hash(url: str) -> str:
    return hashlib.sha256(url.encode()).hexdigest()


def run(session) -> int:
    """Returns count of newly-added URLs. Idempotent: re-running with the same feed content
    upserts the same rows, never double-counts (unique key = url_hash)."""
    urls = fetch_feed()
    now = datetime.now(timezone.utc)
    new_count = 0

    for url in urls:
        h = url_hash(url)
        existing = session.get(ThreatIntelUrl, h)
        if existing is None:
            session.add(ThreatIntelUrl(url_hash=h, url=url, first_seen=now, source="openphish"))
            new_count += 1

    session.commit()
    return new_count
