"""Generates ground-truth tokenizer fixtures for the Kotlin AlbertUnigramTokenizer port
(AlbertUnigramTokenizerTest in the Android app).

Runs the REAL HuggingFace tokenizer (not a reimplementation) to get authoritative
input_ids/attention_mask for a set of sample messages spanning the supported languages. The
Kotlin unit test asserts exact token-id parity against this file, so a tokenizer port bug is
caught without needing a physical device.

Usage:
    python scripts/generate_tokenizer_fixtures.py --model_dir final_model_20260706_123010 \
        --out ../SurakshaSathi/app/src/test/resources/tokenizer_fixtures.json
"""

import argparse
import json
import os

MAX_SEQ_LEN = 64

SAMPLES = [
    ("plain_ham_en", "Hey, are we still meeting for lunch tomorrow at 1pm?"),
    ("plain_ham_hi", "क्या आप कल शाम को फ्री हैं? मुझे आपसे मिलना है।"),
    (
        "phishing_apk_en",
        "Dear Customer, your SBI YONO account has been suspended. Download yono-update.apk now to restore access: http://fake-sbi.xyz/yono.apk",
    ),
    (
        "phishing_kyc_hi",
        "प्रिय ग्राहक, आपका SBI खाता निलंबित कर दिया गया है। तुरंत केवाईसी अपडेट करें: http://sbi-kyc.xyz/update",
    ),
    (
        "phishing_otp_mr",
        "तुमचा बँक खाते ब्लॉक होणार आहे. OTP शेअर करा त्वरित सत्यापित करण्यासाठी.",
    ),
    (
        "phishing_reward_ta",
        "வாழ்த்துக்கள்! நீங்கள் 50,000 ரூபாய் வென்றுள்ளீர்கள். இப்போது கிளிக் செய்து உரிமை கோருங்கள்: bit.ly/claim123",
    ),
    ("hinglish_ham", "Kal office aa raha hoon, milte hain lunch pe"),
    (
        "phishing_hinglish",
        "Aapka account block ho jayega. Turant apna KYC update karein is link par click karke: bit.ly/kyc-update",
    ),
    ("emoji_ham_en", "Happy birthday!! 🎉🎂 hope you have a great day 😊"),
    ("mixed_script", "Your parcel देरी से आ रहा है, track karein: http://track-parcel.xyz/123"),
    ("empty", ""),
    ("single_word", "Hello"),
    ("punctuation_heavy", "WOW!!! Really??? ...ok then."),
]


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--model_dir", required=True)
    parser.add_argument("--out", required=True)
    args = parser.parse_args()

    from transformers import AutoTokenizer

    tokenizer = AutoTokenizer.from_pretrained(args.model_dir)

    threshold_path = os.path.join(args.model_dir, "optimal_threshold.json")
    with open(threshold_path, encoding="utf-8") as f:
        threshold = json.load(f)["optimal_threshold"]

    fixtures = []
    for name, text in SAMPLES:
        enc = tokenizer(text, max_length=MAX_SEQ_LEN, truncation=True, padding="max_length")
        fixtures.append(
            {
                "name": name,
                "text": text,
                "input_ids": enc["input_ids"],
                "attention_mask": enc["attention_mask"],
            }
        )
        print(f"{name:20s} tokens={sum(enc['attention_mask'])}  text={text[:50]!r}")

    os.makedirs(os.path.dirname(args.out), exist_ok=True)
    with open(args.out, "w", encoding="utf-8") as f:
        json.dump({"optimal_threshold": threshold, "max_seq_len": MAX_SEQ_LEN, "fixtures": fixtures}, f, indent=2, ensure_ascii=False)
    print(f"\nWrote {len(fixtures)} fixtures to {args.out}")


if __name__ == "__main__":
    main()
