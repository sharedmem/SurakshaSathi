"""Generates ground-truth feature-extractor fixtures for the Kotlin FeatureExtractor +
UrlIntelligenceStage2 port (dims 0-33 of the 39-dim auxiliary vector) used by
TFLiteSpamClassifier in the Android app.

Runs the REAL Python `FeatureExtractor` + `Stage2URLIntelligence` (not a reimplementation) to
get authoritative feature vectors for a set of sample messages -- including the two real
malicious messages from the field (stock-tip scam, traffic-challan APK lure) that originally
motivated this port. Stage 3 (live HTTP, dims 34-38) is intentionally NOT run here, matching
what the on-device Kotlin classifier does -- so the fixture's dims 34-38 are always zero and
the Kotlin test only asserts dims 0-33.

Usage:
    python scripts/generate_feature_extractor_fixtures.py --config configs/config.yaml \
        --out ../SurakshaSathi/app/src/test/resources/feature_extractor_fixtures.json
"""

import argparse
import json
import os
import sys

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))

from src.feature_extractor import load_feature_extractor, URLFeatureEnricher, pick_primary_url
from src.url_intelligence import Stage2URLIntelligence

SAMPLES = [
    (
        "field_stock_tip_scam",
        "Grindwell Norton  BUY CMP-1802 target-1900,2000 SL-1700 Pre-open accumulation zone. Strategy ready. Reply TOOL to view: bit.ly/gwrd-tip",
        None,
        None,
    ),
    (
        "field_traffic_challan_apk",
        "Your vehicle has a pending e-Challan of Rs.500. Pay immediately or license will be suspended. Install: mparivahan-challan.icu/Challan.apk",
        None,
        None,
    ),
    (
        "plain_ham_en",
        "Hey, are we still meeting for lunch tomorrow at 1pm?",
        None,
        None,
    ),
    (
        "otp_ham_en",
        "123456 is your OTP for login to XYZ Bank NetBanking. Valid for 10 minutes. Do not share with anyone.",
        None,
        None,
    ),
    (
        "phishing_link_spam",
        "Dear Customer, your SBI YONO account has been suspended. Download yono-update.apk now to restore access: http://fake-sbi.xyz/yono.apk",
        None,
        None,
    ),
    (
        "kyc_callback_fraud_urdu",
        "آپ کی KYC میعاد وقت ختم ہو گئی ہے۔ ہمیں کال کریں: 18001234567",
        None,
        None,
    ),
    (
        "benign_urdu_conversational",
        "آپ کیسے ہیں؟ آج موسم بہت اچھا ہے۔ شام کو ملتے ہیں۔",
        None,
        None,
    ),
    (
        "degenerate_repetition",
        "hi hi hi hi hi hi hi hi hi hi hi hi",
        None,
        None,
    ),
    (
        "wedding_invitation_apk",
        "Aap sabko hamari shaadi ki invitation! Card dekhne ke liye app install karein: shaadi-invite.top/Wedding.apk",
        None,
        None,
    ),
    (
        "loan_app_pattern",
        "Instant loan approved! Personal loan up to 5 lakh, download app now: quickloan-app.club/apply",
        None,
        None,
    ),
    (
        "govt_transport_challan",
        "mParivahan: Your DL license has expired, renew now: parivahan-renew.xyz/renew",
        None,
        None,
    ),
    (
        "brand_impersonation_domain",
        "Your SBI account needs verification, click here: http://sbi-secure-verify.xyz/login",
        None,
        None,
    ),
    (
        "qr_apk_payload",
        "Scan the QR code below to install our new banking security app",
        None,
        "Install: rbl-protect.icu/RBL_Secure.apk",
    ),
    (
        "sender_shortcode_reward",
        "Congratulations! You have won a lucky draw prize of Rs 10000. Claim your reward now: bit.ly/claim99",
        "56070",
        None,
    ),
    ("empty", "", None, None),
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--config", default="configs/config.yaml")
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    fe = load_feature_extractor(args.config)
    s2 = Stage2URLIntelligence()

    fixtures = []
    for name, text, sender, qr_decoded_text in SAMPLES:
        metadata = {}
        if sender:
            metadata["sender"] = sender
        if qr_decoded_text:
            metadata["qr_decoded_text"] = qr_decoded_text

        result = fe.extract(text, metadata)

        if result.extracted_urls:
            primary_url = pick_primary_url(result.extracted_urls)
            if primary_url:
                stage2 = s2.analyze(primary_url)
                URLFeatureEnricher.enrich(result, {"stage2": stage2})
                fe.refresh_after_url_enrichment(result)

        vector = result.to_feature_vector()
        fixtures.append(
            {
                "name": name,
                "text": text,
                "sender": sender,
                "qr_decoded_text": qr_decoded_text,
                "feature_vector_0_33": [round(v, 6) for v in vector[:34]],
                "rule_confidence": round(result.rule_confidence, 6),
                "rule_based_spam": result.rule_based_spam,
                "is_likely_benign": result.is_likely_benign,
                "extracted_urls": result.extracted_urls,
                "sideloading_vectors": result.sideloading_vectors,
            }
        )
        print(f"{name:30s} rule_confidence={result.rule_confidence:.3f}  spam={result.rule_based_spam}")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"fixtures": fixtures}, f, indent=2, ensure_ascii=False)
    print(f"\nWrote {len(fixtures)} fixtures to {args.out}")


if __name__ == "__main__":
    main()
