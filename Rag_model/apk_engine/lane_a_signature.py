"""Lane A: fast (ms-scale) identity check. Cache-first by (package_name + cert_sha256) against a
locally-scraped Play Store top-charts registry; on cache miss, falls back to VirusTotal v3 (the
real substitute for a public "Google Play Protect API" for third-party APKs -- no such public
API exists; VirusTotal already aggregates a Play-Protect-derived signal from partner engines).
"""

import hashlib

import httpx

from common.resilience import with_retry
from config.settings import settings
from db.redis_client import cache_get, cache_set

VT_FILE_REPORT_URL = "https://www.virustotal.com/api/v3/files/{file_hash}"
CACHE_TTL_SECONDS = 7 * 24 * 3600  # a known-good/known-bad cert verdict rarely changes week to week


def cache_key(package_name: str, cert_sha256: str) -> str:
    return "apk_sig:" + hashlib.sha256(f"{package_name}:{cert_sha256}".encode()).hexdigest()


def check_local_registry(package_name: str, cert_sha256: str) -> str | None:
    """"Benign" | "Malicious" | None (not in registry). Registry populated offline by
    scripts/scrape_play_store_registry.py (PlayScraper, off-peak scheduled job)."""
    return cache_get(cache_key(package_name, cert_sha256))


@with_retry("virustotal", exceptions=(httpx.HTTPError,))
def _query_virustotal(file_sha256: str) -> dict:
    if not settings.virustotal_api_key:
        raise RuntimeError("VIRUSTOTAL_API_KEY not set -- see .env.example")
    headers = {"x-apikey": settings.virustotal_api_key}
    resp = httpx.get(VT_FILE_REPORT_URL.format(file_hash=file_sha256), headers=headers, timeout=5)
    resp.raise_for_status()
    return resp.json()


def lane_a_verdict(package_name: str, cert_sha256: str, apk_file_sha256: str) -> str:
    """Returns "malicious" | "benign" | "unknown" -- feeds the Fusion Core's case matrix."""
    registry_hit = check_local_registry(package_name, cert_sha256)
    if registry_hit is not None:
        return registry_hit.lower()

    try:
        vt_result = _query_virustotal(apk_file_sha256)
    except Exception:
        return "unknown"  # VT unavailable -- Fusion Core must not block solely on Lane A's absence

    stats = vt_result.get("data", {}).get("attributes", {}).get("last_analysis_stats", {})
    malicious_count = stats.get("malicious", 0) + stats.get("suspicious", 0)
    verdict = "malicious" if malicious_count >= 3 else "benign" if malicious_count == 0 else "unknown"

    cache_set(cache_key(package_name, cert_sha256), verdict, CACHE_TTL_SECONDS)
    return verdict
