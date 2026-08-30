"""
Signal Test Suite — QR / URL / Callback-vishing regression tests
==================================================================
Runs a curated set of messages that specifically exercise the QR-code,
URL-intelligence, and callback/vishing detection paths (the hard rules in
src/inference.py + the heuristics in src/feature_extractor.py +
src/url_intelligence.py) through the full SMSAnalyzer pipeline, then dumps
one JSON file summarizing every test + its result.

The JSON is meant to be handed to an LLM (or a human reviewer) as
additional context on top of the raw per-message classification — e.g.
"here is exactly which rule/heuristic fired and why" for every case,
rather than just a bare pass/fail count.

Usage:
    python src/signal_tests.py --config configs/config.yaml --model final_model_YYYYMMDD_HHMMSS \
        --output final_model_YYYYMMDD_HHMMSS/evaluation_results/signal_tests.json
"""

import argparse
import json
import os
import sys
from typing import Any, Dict, List

sys.path.append(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


# Each case: id, category, message text, optional metadata (sender / qr_decoded_text),
# expected label ("spam"/"ham"), and a short note on what it's regression-testing.
TEST_CASES: List[Dict[str, Any]] = [
    # ── QR code payloads (Hard Rules 2 & 4) ──────────────────────────────
    {
        "id": "qr_apk_payload_bn",
        "category": "qr",
        "text": "আপনার SBI KYC মেয়াদ শেষ। আজই অ্যাপ ইনস্টল করুন নইলে অ্যাকাউন্ট বন্ধ।",
        "metadata": {"qr_decoded_text": "https://sbi-kyc-update.xyz/SBI_Secure.apk"},
        "expected": "spam",
        "note": "QR decoded payload is a direct .apk URL -> HARD_RULE_2 (qr_apk_payload).",
    },
    {
        "id": "qr_upi_collect_en",
        "category": "qr",
        "text": "Scan the QR code to claim your ₹5000 Flipkart cashback reward!",
        "metadata": {"qr_decoded_text": "upi://collect?pa=scam@upi&pn=FlipkartReward&am=5000&mode=00"},
        "expected": "spam",
        "note": "QR decoded payload is a upi://collect mandate -> HARD_RULE_4 (qr_upi_collect).",
    },
    {
        "id": "qr_reference_only_no_decode_bn",
        "category": "qr",
        "text": "প্রিয় গ্রাহক,\nDisney+ Hotstar: Your membership has expired.\nঅনুগ্রহ করে অবিলম্বে ব্যবস্থা নিন, নতুবা আপনার অ্যাকাউন্ট বন্ধ হয়ে যাবে।\nএই বার্তার সাথে সংযুক্ত কিউআর কোডটি স্ক্যান করুন এবং অবিলম্বে যাচাই করুন।",
        "metadata": {},
        "expected": "spam",
        "note": "Message references a QR attachment but no qr_decoded_text was supplied "
                "(simulates the phone not having decoded it yet / a text-only export). "
                "Must still be flagged via has_qr_reference + urgency + salutation.",
    },
    {
        "id": "qr_legitimate_payment_control",
        "category": "qr",
        "text": "Scan QR at checkout to pay your table bill instantly.",
        "metadata": {"qr_decoded_text": "upi://pay?pa=merchant@okhdfcbank&pn=CafeCorner&am=450"},
        "expected": "spam",
        "note": "Deliberately conservative control: any upi:// collect/pay/mandate QR is "
                "treated as spam by design (Hard Rule 4 has no legitimate-merchant carve-out) "
                "— documents current behavior rather than asserting it can't be tightened later.",
    },

    # ── URL intelligence: shorteners, brand impersonation, direct APK ────
    {
        "id": "url_bare_shortener_no_apk_word",
        "category": "url",
        "text": "SBI Free Msg: Your bill is paid. Thanks, here's a little gift for you: cutt.ly/fakeapp",
        "metadata": {},
        "expected": "spam",
        "note": "Shortened URL with no literal 'apk'/'app' keyword and no reward "
                "superlative — must be caught via Stage2 shortener risk bump + "
                "rule_confidence fallback, not lexical keyword matching alone.",
    },
    {
        "id": "url_direct_apk_link",
        "category": "url",
        "text": "Namaste, aapka RBL Bank account block ho gaya. Install karo: rbl-protect-acc.icu/RBL_Protect.apk",
        "metadata": {},
        "expected": "spam",
        "note": "Direct .apk link in text -> HARD_RULE_1 (direct_apk_link).",
    },
    {
        "id": "url_brand_impersonation_typosquat",
        "category": "url",
        "text": "Your account needs verification. Visit: sbi-secure-updat.in/verify",
        "metadata": {},
        "expected": "spam",
        "note": "Typosquatted brand domain on a low-reputation-style .in TLD "
                "-> Stage2 brand impersonation + suspicious domain signal.",
    },
    {
        "id": "url_legitimate_ecommerce_control",
        "category": "url",
        "text": "Your Flipkart order #OD8847120 shipped. Delivery by 28 Jun. Track: fkrt.it/t8xB",
        "metadata": {},
        "expected": "ham",
        "note": "Legitimate delivery notification with an official-brand short link — "
                "control case to check the URL heuristics don't over-trigger on shorteners alone.",
    },

    # ── Callback / vishing (Hard Rules 3 & 6, no phone number required) ──
    {
        "id": "callback_apk_install_prompt",
        "category": "callback",
        "text": "Your SBI account suspended. Call 1800-111-2222 now. Our officer will help you install the security app to restore access.",
        "metadata": {"sender": "+447911123456"},
        "expected": "spam",
        "note": "Call + officer installs app -> HARD_RULE_3 (callback_apk_install_prompt).",
    },
    {
        "id": "callback_financial_alert_no_phone_no_kyc_pa",
        "category": "callback",
        "text": "INR 1500 was debited from your SBI account. If this was not done by you, secure your account now.\n"
                "ਕਿਰਪਾ ਕਰਕੇ ਅੱਜ ਹੀ ਕਾਰਵਾਈ ਕਰੋ, ਨਹੀਂ ਤਾਂ ਤੁਹਾਡਾ ਖਾਤਾ ਬਲਾਕ ਹੋ ਜਾਵੇਗਾ।\n"
                "We tried to reach you regarding your pending issue. Call us back on the number below and our representative will guide you through the verification process.",
        "metadata": {},
        "expected": "spam",
        "note": "No phone number literal, no KYC keyword, no URL, mixed Punjabi+English "
                "-> must fire via the generic financial-alert+callback authority rule "
                "(HARD_RULE_6), not just an explicit-phone-number heuristic.",
    },
    {
        "id": "callback_legitimate_alert_with_real_helpline_control",
        "category": "callback",
        "text": "Transaction alert: ₹2500 debited from A/C XX7891 at Amazon. Avl Bal: ₹12000. Not you? Call 1800XXXXXXX",
        "metadata": {},
        "expected": "ham",
        "note": "Genuine bank alert that proactively gives an actual helpline number, rather "
                "than vaguely saying 'call us back' / 'our representative' / 'number below' — "
                "control case for the callback-fraud rule's false-positive guard.",
    },

    # ── Anti-false-positive guards (non-spam edge cases) ─────────────────
    {
        "id": "benign_repeated_word_noise_mr",
        "category": "anti_fp",
        "text": "मी देर आहे, तर तुम्हाला उद्या रात्री कॉल करा, मी आणि तू "
                "मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी मी.",
        "metadata": {},
        "expected": "ham",
        "note": "Degenerate word-repetition glitch text with a call-back verb but zero "
                "attack surface (no URL/APK/QR) -> is_likely_benign guard must keep this ham "
                "even if the ML model alone gives a borderline spam probability.",
    },
    {
        "id": "benign_mixed_script_conversational",
        "category": "anti_fp",
        "text": "ভাই, আজ রাতে dinner কোথায় করবো? তুমি বলো।",
        "metadata": {},
        "expected": "ham",
        "note": "Benign Bengali+English code-mixed chat -> script-mixing alone must not "
                "trigger spam.",
    },
    {
        "id": "benign_legitimate_otp",
        "category": "anti_fp",
        "text": "Your OTP for SBI NetBanking is 847291. Valid 10 mins. Do not share. -SBI",
        "metadata": {"sender": "VM-SBIBNK"},
        "expected": "ham",
        "note": "Legitimate transactional OTP control case.",
    },
]


def run_signal_tests(config_path: str, model_path: str) -> Dict[str, Any]:
    from src.inference import SMSAnalyzer

    analyzer = SMSAnalyzer(config_path=config_path, model_path=model_path)

    results = []
    for case in TEST_CASES:
        r = analyzer.analyze(case["text"], case.get("metadata", {}))
        predicted = "spam" if r["is_spam"] else "ham"
        passed = predicted == case["expected"]
        results.append({
            "id": case["id"],
            "category": case["category"],
            "message": case["text"],
            "metadata": case.get("metadata", {}),
            "expected_label": case["expected"],
            "predicted_label": predicted,
            "passed": passed,
            "note": case["note"],
            "spam_probability": r["spam_probability"],
            "threshold_used": r["threshold_used"],
            "triggered_by": r["triggered_by"],
            "hard_rule_reason": r["hard_rule_reason"],
            "rule_confidence": r["rule_confidence"],
            "sideloading_vectors": r["sideloading_vectors"],
            "extracted_urls": r["extracted_urls"],
            "detected_brands": r["detected_brands"],
            "qr_payload_type": r["qr_payload_type"],
            "callback_signals": r["callback_signals"],
        })

    by_category: Dict[str, Dict[str, int]] = {}
    for res in results:
        cat = by_category.setdefault(res["category"], {"total": 0, "passed": 0})
        cat["total"] += 1
        cat["passed"] += int(res["passed"])

    summary = {
        "total_tests": len(results),
        "passed": sum(r["passed"] for r in results),
        "failed": sum(not r["passed"] for r in results),
        "pass_rate": round(sum(r["passed"] for r in results) / len(results), 4) if results else None,
        "by_category": by_category,
        "model_path": model_path,
    }

    return {"summary": summary, "tests": results}


if __name__ == "__main__":
    ap = argparse.ArgumentParser(description="Run QR/URL/callback signal regression tests")
    ap.add_argument("--config", default="configs/config.yaml")
    ap.add_argument("--model", required=True, help="Path to a trained model dir")
    ap.add_argument("--output", default=None,
                     help="Output JSON path (default: <model>/evaluation_results/signal_tests.json)")
    args = ap.parse_args()

    output_path = args.output or os.path.join(args.model, "evaluation_results", "signal_tests.json")
    os.makedirs(os.path.dirname(output_path), exist_ok=True)

    report = run_signal_tests(args.config, args.model)
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(report, f, indent=2, ensure_ascii=False)

    print(json.dumps(report["summary"], indent=2, ensure_ascii=False))
    print(f"\nFull report written to: {output_path}")
