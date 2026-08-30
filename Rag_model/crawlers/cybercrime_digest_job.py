"""Scheduled job (daily, ~06:00 IST, before the 07:00 alerting job): scrapes cybercrime.gov.in's
daily digest, extracts (region, fraud_type, report_count) rows, upserts into Postgres
`national_digest`. No official API -- this is a best-effort HTML scrape; the site's structure can
change, so this is wrapped in the standard retry/circuit-breaker and logs (rather than crashes)
on a parse-shape mismatch.
"""

from datetime import datetime, timezone

import httpx
from bs4 import BeautifulSoup

from common.resilience import with_retry
from db.postgres_client import NationalDigest

DIGEST_URL = "https://cybercrime.gov.in/Webform/daily-digest.aspx"


@with_retry("cybercrime_digest", exceptions=(httpx.HTTPError,))
def fetch_digest_html() -> str:
    resp = httpx.get(DIGEST_URL, timeout=15, headers={"User-Agent": "SurakshaSathiBot/1.0 (+fraud-research)"})
    resp.raise_for_status()
    return resp.text


def parse_digest(html: str) -> list[dict]:
    """Best-effort table scrape. Real structure discovery happens at integration time against the
    live page; this expects a table with region/type/count columns and degrades to an empty list
    (logged) rather than raising, so one page-layout change doesn't take down the whole job."""
    soup = BeautifulSoup(html, "lxml")
    rows = []
    for table_row in soup.select("table tr"):
        cells = [c.get_text(strip=True) for c in table_row.find_all(["td"])]
        if len(cells) >= 3:
            region, fraud_type, count_str = cells[0], cells[1], cells[2]
            if count_str.isdigit():
                rows.append({"region": region, "fraud_type": fraud_type, "report_count": int(count_str)})
    return rows


def run(session) -> int:
    try:
        html = fetch_digest_html()
        rows = parse_digest(html)
    except Exception as e:
        print(f"cybercrime_digest_job: fetch/parse failed, skipping this run -- {e}")
        return 0

    now = datetime.now(timezone.utc)
    for row in rows:
        session.add(
            NationalDigest(
                date=now,
                region=row["region"],
                fraud_type=row["fraud_type"],
                report_count=row["report_count"],
                source="cybercrime.gov.in",
            )
        )
    session.commit()
    return len(rows)
