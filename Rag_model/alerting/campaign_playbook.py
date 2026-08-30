"""Static lure/APK-theme/intervention playbook, one entry per `message_type` (see
clustering/message_type.py). This is what turns a raw message-type label into the human-readable
"ongoing campaign" row shown when a user taps a region on the Feature Map: which lure is being
used, what fake app theme it drops, and what a GenAI-style intervention should tell the user.

Not derived from data -- these are editorial/expert-authored mappings (mirrors how a real threat
intel team would maintain a campaign playbook), keyed to this repo's existing message_type
taxonomy rather than inventing a parallel one.
"""

from dataclasses import dataclass

from clustering.message_type import ALL_MESSAGE_TYPES, FALLBACK_TYPE


@dataclass(frozen=True)
class CampaignPlaybookEntry:
    lure_label: str
    malicious_apk_theme: str
    intervention: str


CAMPAIGN_PLAYBOOK: dict[str, CampaignPlaybookEntry] = {
    "aadhaar_kyc": CampaignPlaybookEntry(
        lure_label="Aadhaar / KYC Update",
        malicious_apk_theme="Fake UIDAI/bank KYC-update app",
        intervention="No agency asks you to install an app or share an Aadhaar OTP via an SMS "
        "link -- verify only through the official UIDAI app or mAadhaar.",
    ),
    "tax_refund": CampaignPlaybookEntry(
        lure_label="Income Tax Refund",
        malicious_apk_theme="Fake Income Tax Department app",
        intervention="The Income Tax Department never sends refund links via SMS -- check refund "
        "status only on the official incometax.gov.in portal.",
    ),
    "electricity_utility": CampaignPlaybookEntry(
        lure_label="Electricity Bill / Disconnection Notice",
        malicious_apk_theme="Fake utility-payment app",
        intervention="Power utilities do not disconnect service over an SMS link -- pay bills only "
        "via your discom's official app or website.",
    ),
    "bank_alert": CampaignPlaybookEntry(
        lure_label="Bank Account / Card Alert",
        malicious_apk_theme="Fake banking / YONO-lookalike app",
        intervention="Your bank never asks for an OTP, PIN, or card details via an SMS link -- "
        "verify by calling the number printed on your card, not one from the message.",
    ),
    "otp_share": CampaignPlaybookEntry(
        lure_label="OTP Verification Request",
        malicious_apk_theme="OTP-stealing overlay app",
        intervention="Never share an OTP with anyone, including callers claiming to be bank or "
        "police staff -- a genuine OTP request never needs to be read aloud or forwarded.",
    ),
    "parcel_delivery": CampaignPlaybookEntry(
        lure_label="Parcel / Courier Delivery Issue",
        malicious_apk_theme="Fake courier-tracking app",
        intervention="Track parcels only via the courier's official app or website -- never "
        "install an APK linked from a delivery SMS.",
    ),
    "telecom_offer": CampaignPlaybookEntry(
        lure_label="Telecom Recharge / Data Offer",
        malicious_apk_theme="Fake telecom-offer app",
        intervention="Recharge only via your operator's official app or USSD code -- ignore "
        "'special offer' APKs linked from SMS.",
    ),
    "ecommerce_discount": CampaignPlaybookEntry(
        lure_label="E-commerce Flash Sale / Prize",
        malicious_apk_theme="Fake shopping-app clone",
        intervention="Shop only via the verified Play Store app -- an unsolicited 'you've won an "
        "iPhone' offer is almost always a credential-harvesting trap.",
    ),
    "lottery_prize": CampaignPlaybookEntry(
        lure_label="Lottery / Lucky Draw Win",
        malicious_apk_theme="Prize-claim credential-harvesting app",
        intervention="There is no lottery you didn't enter -- never pay a 'processing fee' or "
        "install an app to claim a prize.",
    ),
    "job_offer": CampaignPlaybookEntry(
        lure_label="Govt Job Recruitment / Work-From-Home Offer",
        malicious_apk_theme="Fake government-recruitment / HR-portal app",
        intervention="No government job or legitimate employer requires installing an app or "
        "paying upfront -- verify only on the official recruitment or careers website.",
    ),
    "loan_app": CampaignPlaybookEntry(
        lure_label="Instant Pre-Approved Loan",
        malicious_apk_theme="Predatory loan-harvesting APK",
        intervention="Use only RBI-registered NBFC apps listed on the Play Store -- an instant "
        "SMS-linked loan APK is a common data-harvesting and harassment trap.",
    ),
    "govt_benefit": CampaignPlaybookEntry(
        lure_label="Government Scheme / Subsidy",
        malicious_apk_theme="Fake government-scheme app",
        intervention="Government benefit schemes are applied for only via official portals (e.g. "
        "pmkisan.gov.in) -- never via an SMS-linked APK.",
    ),
    "wedding_invitation": CampaignPlaybookEntry(
        lure_label="Digital Wedding Invitation",
        malicious_apk_theme="Malware-laced invitation APK",
        intervention="Legitimate e-invitations are web links or PDFs, never an installable app -- "
        "don't install anything to 'view' an invitation.",
    ),
    FALLBACK_TYPE: CampaignPlaybookEntry(
        lure_label="Unclassified Suspicious Message",
        malicious_apk_theme="Unknown / generic malicious app",
        intervention="When in doubt, don't click the link or install the app -- verify directly "
        "through the organization's official channel before acting.",
    ),
}

assert set(CAMPAIGN_PLAYBOOK) == set(ALL_MESSAGE_TYPES), (
    "CAMPAIGN_PLAYBOOK must have exactly one entry per clustering.message_type.ALL_MESSAGE_TYPES"
)


def playbook_entry(message_type: str | None) -> CampaignPlaybookEntry:
    return CAMPAIGN_PLAYBOOK.get(message_type or FALLBACK_TYPE, CAMPAIGN_PLAYBOOK[FALLBACK_TYPE])
