"""L1 common triage (<15ms budget): regex tokenizer extracts URLs/phone numbers/image attachments
from the message, then checks each token against the in-process Bloom filter (see
db/redis_client.py for why it's in-process, not Redis-backed). A Bloom hit fast-paths straight to
the LLM with a high-confidence flag, skipping L2 entirely.
"""

import re
from dataclasses import dataclass, field

from db.redis_client import ThreatBloomFilter

URL_RE = re.compile(r"https?://[^\s]+")
# E.164-ish + common Indian local formats
PHONE_RE = re.compile(r"(?:\+91[\-\s]?)?[6-9]\d{9}\b")

_bloom = ThreatBloomFilter()  # rebuilt from Postgres threat_intel_urls on a refresh schedule


@dataclass
class TriageResult:
    urls: list[str] = field(default_factory=list)
    phone_numbers: list[str] = field(default_factory=list)
    has_image_attachment: bool = False
    bloom_hit: bool = False
    bloom_hit_value: str | None = None


def triage(message_text: str, has_image_attachment: bool = False) -> TriageResult:
    urls = URL_RE.findall(message_text)
    phones = PHONE_RE.findall(message_text)

    bloom_hit = False
    bloom_hit_value = None
    for token in urls + phones:
        if _bloom.might_contain(token):
            bloom_hit = True
            bloom_hit_value = token
            break

    return TriageResult(
        urls=urls,
        phone_numbers=phones,
        has_image_attachment=has_image_attachment,
        bloom_hit=bloom_hit,
        bloom_hit_value=bloom_hit_value,
    )


def refresh_bloom_filter(known_bad_urls: list[str], known_bad_numbers: list[str]) -> None:
    """Called on instance startup + on a schedule (paired with the OpenPhish 12h poll) to rebuild
    the in-process filter from Postgres."""
    for url in known_bad_urls:
        _bloom.add(url)
    for number in known_bad_numbers:
        _bloom.add(number)
