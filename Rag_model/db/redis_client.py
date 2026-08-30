"""Memorystore for Redis (GCP-native managed Redis) client: hot-path caching for WHOIS domain-age
lookups and VirusTotal results, so repeated queries for the same key don't re-hit paid/rate-
limited APIs within their TTL.

The L1 triage Bloom filter is NOT stored in Redis: Memorystore doesn't support the RedisBloom
module (that requires Redis Enterprise), so `ThreatBloomFilter` below is an in-process structure
instead, rebuilt from Postgres `threat_intel_urls` (+ known-bad phone/cert tables) on each Cloud
Run instance's startup and on a refresh schedule. A Bloom hit is a fast "definitely worth a closer
look" signal for L1 triage; it is never the sole basis for a BLOCK verdict (false-positive rate > 0).

Everything here is optional-safe: REDIS_URL is empty until Memorystore is provisioned, and the
rest of the pipeline must keep working (just without the cache speedup) rather than crash.
"""

import redis
from pybloom_live import ScalableBloomFilter

from config.settings import settings

_redis_client: redis.Redis | None = None
_redis_checked = False


def get_redis() -> redis.Redis | None:
    """Returns a connected client, or None if Redis isn't configured/reachable. Callers must
    treat None as "no cache available" and fall through to the real lookup -- never crash."""
    global _redis_client, _redis_checked
    if _redis_checked:
        return _redis_client
    _redis_checked = True
    if not settings.redis_url:
        return None
    try:
        client = redis.from_url(settings.redis_url, decode_responses=True, socket_connect_timeout=1)
        client.ping()
        _redis_client = client
    except redis.RedisError:
        _redis_client = None
    return _redis_client


def cache_get(key: str) -> str | None:
    client = get_redis()
    if client is None:
        return None
    try:
        return client.get(key)
    except redis.RedisError:
        return None


def cache_set(key: str, value: str, ttl_seconds: int) -> None:
    client = get_redis()
    if client is None:
        return
    try:
        client.set(key, value, ex=ttl_seconds)
    except redis.RedisError:
        pass  # cache is an optimization, not a dependency -- swallow and move on


class ThreatBloomFilter:
    """In-process Bloom filter (see module docstring for why this isn't Redis-backed)."""

    def __init__(self, initial_capacity: int = 200_000, error_rate: float = 0.001):
        self._filter = ScalableBloomFilter(
            initial_capacity=initial_capacity, error_rate=error_rate
        )

    def add(self, key: str) -> None:
        self._filter.add(key)

    def might_contain(self, key: str) -> bool:
        return key in self._filter
