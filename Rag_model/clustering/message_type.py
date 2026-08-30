"""Derives a genuine fraud-narrative `message_type` from the message text itself via keyword
matching -- NOT from the raw CSVs' `category` column, which for ~83% of rows just says
"public_dataset"/"raw_dataset" (the source file, not a fraud type) rather than anything
describing the message's actual content. This is a real signal computed from real text, not a
fabricated label.

Order matters: more specific patterns are checked before generic fallbacks.
"""

import re

MESSAGE_TYPE_PATTERNS: list[tuple[str, re.Pattern]] = [
    ("aadhaar_kyc", re.compile(r"aadhaar|kyc|uidai", re.IGNORECASE)),
    ("tax_refund", re.compile(r"income tax|tax refund|itr\b", re.IGNORECASE)),
    ("electricity_utility", re.compile(r"electricity|power bill|disconnect", re.IGNORECASE)),
    ("bank_alert", re.compile(r"bank account|unusual login|debit card|credit card|cashback|hdfc|axis|sbi|icici", re.IGNORECASE)),
    ("otp_share", re.compile(r"\botp\b|one.time password", re.IGNORECASE)),
    ("parcel_delivery", re.compile(r"parcel|courier|fedex|delivery|tracking (code|number)", re.IGNORECASE)),
    ("telecom_offer", re.compile(r"\b(jio|airtel|vi|vodafone|bsnl)\b|\bgb\b.*data|recharge", re.IGNORECASE)),
    ("ecommerce_discount", re.compile(r"amazon|flipkart|myntra|% off|discount|sale|iphone|winner", re.IGNORECASE)),
    ("lottery_prize", re.compile(r"lottery|lucky draw|you.?ve won|congratulations.*won|prize", re.IGNORECASE)),
    ("job_offer", re.compile(r"job offer|work from home|earn.*(per day|daily)|part.?time job", re.IGNORECASE)),
    ("loan_app", re.compile(r"loan (approved|offer)|instant loan|pre.?approved loan", re.IGNORECASE)),
    ("govt_benefit", re.compile(r"pm kisan|govt\.? scheme|subsidy|ration card|pension", re.IGNORECASE)),
    ("wedding_invitation", re.compile(r"wedding|marriage invit|shaadi", re.IGNORECASE)),
]

FALLBACK_TYPE = "generic_phishing"

ALL_MESSAGE_TYPES = [name for name, _ in MESSAGE_TYPE_PATTERNS] + [FALLBACK_TYPE]


def classify_message_type(text: str) -> str:
    for type_name, pattern in MESSAGE_TYPE_PATTERNS:
        if pattern.search(text):
            return type_name
    return FALLBACK_TYPE
