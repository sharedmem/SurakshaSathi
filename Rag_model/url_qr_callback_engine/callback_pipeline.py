"""L2 Path C: callback/vishing number verification.

STIR/SHAKEN does not exist as a queryable framework in India (it's US/Canada-only, ATIS). The
real Indian equivalent is TRAI's DLT (Distributed Ledger) header-registration scheme, which
governs registered bulk-SMS sender IDs -- implemented here as a heuristic pattern check, not an
API call (no public per-message attestation API exists for India). Twilio Lookup v2 gives
carrier/line-type only, NOT India porting history or activation date -- no public API provides
that for India, so `number_lifespan_days` is left unavailable rather than faked.
"""

import re
from dataclasses import dataclass

import httpx

from common.resilience import with_retry
from config.settings import settings

# Registered DLT alphanumeric headers are exactly 6 chars, letters only (e.g. "VM-AIRTL" minus
# the operator prefix) or a 3-letter telco prefix + 3-letter entity code -- a raw 10-digit mobile
# number posing as a "bank" sender is the actual red flag this heuristic targets.
DLT_HEADER_RE = re.compile(r"^[A-Z]{2}-[A-Z0-9]{6}$")
RAW_MOBILE_RE = re.compile(r"^[6-9]\d{9}$")

LURE_KEYWORDS = [
    "kyc", "block", "suspend", "verify immediately", "call back", "customer care",
    "refund", "otp", "penalty", "legal action", "your parcel", "electricity",
]


@dataclass
class CallbackAnalysisResult:
    sender_id_is_dlt_registered_format: bool
    sender_vs_callback_mismatch: bool
    lure_keywords_found: list[str]
    carrier_name: str | None
    line_type: str | None
    number_lifespan_days: None = None  # not available for India via any public API -- see docstring


def sender_id_dlt_check(sender_id: str) -> bool:
    """True if the sender ID matches the registered DLT header shape. A raw 10-digit mobile
    claiming to be a bank/service sender does NOT match -- that mismatch is the actual signal."""
    return bool(DLT_HEADER_RE.match(sender_id))


def detect_context_lures(message_text: str) -> list[str]:
    text_lower = message_text.lower()
    return [kw for kw in LURE_KEYWORDS if kw in text_lower]


@with_retry("twilio_lookup", exceptions=(httpx.HTTPError,))
def twilio_carrier_lookup(phone_number: str) -> tuple[str | None, str | None]:
    """Returns (carrier_name, line_type). Real Twilio Lookup v2 call -- does not return India
    porting history or activation date; no public API does."""
    if not (settings.twilio_account_sid and settings.twilio_auth_token):
        return None, None

    resp = httpx.get(
        f"https://lookups.twilio.com/v2/PhoneNumbers/{phone_number}",
        params={"Fields": "line_type_intelligence"},
        auth=(settings.twilio_account_sid, settings.twilio_auth_token),
        timeout=5,
    )
    resp.raise_for_status()
    data = resp.json()
    line_intel = data.get("line_type_intelligence") or {}
    return line_intel.get("carrier_name"), line_intel.get("type")


def analyze_callback(sender_id: str, callback_number: str | None, message_text: str) -> CallbackAnalysisResult:
    dlt_ok = sender_id_dlt_check(sender_id)
    mismatch = bool(callback_number and RAW_MOBILE_RE.match(callback_number) and not dlt_ok)
    lures = detect_context_lures(message_text)

    carrier, line_type = (None, None)
    if callback_number:
        try:
            carrier, line_type = twilio_carrier_lookup(callback_number)
        except Exception:
            pass  # Twilio down/misconfigured -- proceed without carrier signal, not a hard failure

    return CallbackAnalysisResult(
        sender_id_is_dlt_registered_format=dlt_ok,
        sender_vs_callback_mismatch=mismatch,
        lure_keywords_found=lures,
        carrier_name=carrier,
        line_type=line_type,
    )
