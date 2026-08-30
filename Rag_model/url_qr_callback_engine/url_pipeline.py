"""L2 Path A: URL/PWA analysis. Async HEAD-only redirect chase (no JS rendering, per the <1.5s
per-message latency budget), WHOIS domain-age lookup, PWA manifest.json audit. The lexical/
structural GBC URL classifier is a stub (needs PhishTank + Tranco-1M training, see
scripts/train_url_gbc.py) -- everything else here is real, working integration code.
"""

import math
from collections import Counter
from dataclasses import dataclass, field
from urllib.parse import urljoin, urlparse

import httpx

from common.resilience import with_retry
from config.settings import settings
from db.redis_client import cache_get, cache_set

WHOIS_CACHE_TTL_SECONDS = 24 * 3600  # domain age doesn't change; registrar rarely does


@dataclass
class UrlAnalysisResult:
    resolved_destination: str | None = None
    redirect_chain: list[str] = field(default_factory=list)
    domain_age_days: int | None = None
    registrar_country: str | None = None
    gbc_phishing_score: float | None = None  # stub until trained
    is_pwa: bool = False
    pwa_scope_start_url_mismatch: bool = False
    pwa_name_spoofing_suspected: bool = False
    manifest_entropy: float | None = None


@with_retry("url_head_resolve", exceptions=(httpx.HTTPError,))
def resolve_redirect_chain(url: str, max_hops: int = 8) -> list[str]:
    chain = [url]
    current = url
    timeout = settings.url_head_timeout_ms / 1000
    with httpx.Client(timeout=timeout, follow_redirects=False) as client:
        for _ in range(max_hops):
            resp = client.head(current)
            if resp.status_code in (301, 302, 303, 307, 308) and "location" in resp.headers:
                current = urljoin(current, resp.headers["location"])
                chain.append(current)
            else:
                break
    return chain


def whois_lookup(domain: str) -> tuple[int | None, str | None]:
    """Returns (domain_age_days, registrar_country). Uses WhoisXML API (paid, cached in Redis) --
    free python-whois isn't reliable enough for production (registry rate limits)."""
    cache_key = f"whois:{domain}"
    cached = cache_get(cache_key)
    if cached:
        age_str, country = cached.split("|", 1)
        return (int(age_str) if age_str != "None" else None, country or None)

    if not settings.whoisxml_api_key:
        return None, None

    resp = httpx.get(
        "https://www.whoisxmlapi.com/whoisserver/WhoisService",
        params={"apiKey": settings.whoisxml_api_key, "domainName": domain, "outputFormat": "JSON"},
        timeout=settings.url_head_timeout_ms / 1000,
    )
    resp.raise_for_status()
    record = resp.json().get("WhoisRecord", {})

    from datetime import datetime, timezone

    created_date = record.get("createdDate")
    age_days = None
    if created_date:
        try:
            created = datetime.fromisoformat(created_date.replace("Z", "+00:00"))
            age_days = (datetime.now(timezone.utc) - created).days
        except ValueError:
            pass
    country = record.get("registrant", {}).get("country")

    cache_set(cache_key, f"{age_days}|{country or ''}", WHOIS_CACHE_TTL_SECONDS)
    return age_days, country


def shannon_entropy(s: str) -> float:
    if not s:
        return 0.0
    counts = Counter(s)
    length = len(s)
    return -sum((c / length) * math.log2(c / length) for c in counts.values())


def audit_pwa_manifest(final_url: str) -> dict:
    """Downloads and parses manifest.json if the destination hosts a PWA. Real HTTP + JSON parse,
    no trained model needed."""
    manifest_url = urljoin(final_url, "/manifest.json")
    try:
        resp = httpx.get(manifest_url, timeout=settings.url_head_timeout_ms / 1000)
        if resp.status_code != 200:
            return {"is_pwa": False}
        manifest = resp.json()
    except Exception:
        return {"is_pwa": False}

    scope = manifest.get("scope", "")
    start_url = manifest.get("start_url", "")
    name = manifest.get("name", "")

    scope_domain = urlparse(urljoin(final_url, scope)).netloc
    start_domain = urlparse(urljoin(final_url, start_url)).netloc
    scope_mismatch = bool(scope_domain and start_domain and scope_domain != start_domain)

    metadata_blob = f"{name}{manifest.get('short_name', '')}{manifest.get('icons', '')}"
    return {
        "is_pwa": True,
        "pwa_scope_start_url_mismatch": scope_mismatch,
        "pwa_name_spoofing_suspected": name == "" or manifest.get("short_name") == name,
        "manifest_entropy": shannon_entropy(metadata_blob),
    }


def gbc_phishing_score(resolved_url: str) -> float | None:
    """STUB: 30-feature Gradient Boosting Classifier over lexical/structural URL features
    (subdomain count, brand typosquatting, special chars, etc.). Needs training on
    PhishTank + Tranco-1M (see scripts/train_url_gbc.py) -- not trained yet."""
    return None


def analyze_url(url: str) -> UrlAnalysisResult:
    try:
        chain = resolve_redirect_chain(url)
        final_url = chain[-1]
    except httpx.HTTPError:
        # DNS failure / connection refused / timeout is itself a real signal (many phishing
        # domains get taken down or DNS-blocked) -- report it, don't crash the whole scan.
        return UrlAnalysisResult(resolved_destination=None, redirect_chain=[url])

    domain = urlparse(final_url).netloc

    try:
        age_days, country = whois_lookup(domain)
    except Exception:
        age_days, country = None, None

    pwa_audit = audit_pwa_manifest(final_url)
    score = gbc_phishing_score(final_url)

    return UrlAnalysisResult(
        resolved_destination=final_url,
        redirect_chain=chain,
        domain_age_days=age_days,
        registrar_country=country,
        gbc_phishing_score=score,
        is_pwa=pwa_audit.get("is_pwa", False),
        pwa_scope_start_url_mismatch=pwa_audit.get("pwa_scope_start_url_mismatch", False),
        pwa_name_spoofing_suspected=pwa_audit.get("pwa_name_spoofing_suspected", False),
        manifest_entropy=pwa_audit.get("manifest_entropy"),
    )
