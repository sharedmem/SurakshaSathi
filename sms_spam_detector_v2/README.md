# SMS Sideloading Spam Detector

Fake mobile apps circulated via SMS/WhatsApp, leading to phishing and financial
fraud.

## Pipeline Overview

```
SMS / WhatsApp message
       │
       ▼
Stage 1 — FeatureExtractor  (src/feature_extractor.py)
       │   29 heuristic signals: APK links, QR codes, callback/vishing,
       │   brand impersonation, urgency, financial lure, etc.
       │
       ▼
Stage 2 — SMSAnalyzer  (src/inference.py)
       │   HARD RULES (no threshold):
       │     Rule 1: has_apk_link            → SPAM
       │     Rule 2: has_qr_apk_payload      → SPAM
       │     Rule 3: has_callback_apk_prompt → SPAM
       │     Rule 4: has_qr_upi_collect      → SPAM
       │   Indic-BERT hybrid model for all other messages.
       │   HAM → discard.  SPAM → build llm_payload.
       │
       ▼
Stage 3 — LLMAnalyzer  (src/llm_analysis.py)
           fraud_category, attack_mechanism, victim_advisory,
           urgency_level, data_at_risk, advisory in Hindi.

URL Intelligence  (src/url_intelligence.py)
  Stage 2: Lexical + TLD + brand impersonation + blocklist
  Stage 3: HTTP HEAD preflight + redirect chain + static HTML
  Stage 4: Dynamic sandbox (placeholder, Playwright-ready)

Clustering  (src/cluster_spam.py)
  15 fraud-type labels, unique assignment, sentence-transformers or TF-IDF.
```

## Quick Start

```bash
pip install -r requirements.txt

# Train (HPC with SLURM)
sbatch scripts/train.sh

# Or train locally
python src/train.py --config configs/config.yaml

# Web UI for Android testing (replace path with your versioned model dir)
python src/app.py --config configs/config.yaml --model final_model

# LLM analysis (set backend)
export LLM_BACKEND=anthropic
export ANTHROPIC_API_KEY=sk-ant-...
python src/llm_analysis.py --demo

# URL intelligence
python src/url_intelligence.py https://sbi-secure.icu/SBI.apk

# Cluster spam dataset
python src/cluster_spam.py \
  --input data/raw/my_dataset.csv \
  --output data/clusters/ \
  --label_col true_label \
  --n_clusters 12
```

## Hard Rules (always SPAM, no ML probability needed)

| Rule | Signal | Attack |
|------|--------|--------|
| 1 | Direct `.apk` link in SMS text | Sideloading via SMS |
| 2 | QR code decoded payload → APK URL | Sideloading via QR |
| 3 | "Call us → officer installs app" | Phone-call assisted sideloading |
| 4 | QR UPI collect/mandate request | Money-out QR scam |

## Image / Video Attachments (MMS/WhatsApp)

This classifier is **text-only**. If a message has an image or video
attachment, pass `metadata={"media_type": "image"}` (or `"video"`, or
booleans `has_image`/`has_video`) — the pipeline records that an attachment
was present (`media_ignored=True` in the result, surfaced in the web UI) but
never fetches, decodes, or analyzes the attachment's actual content. The
verdict is based solely on the message text. QR codes are the one exception,
and only via the already-decoded `qr_decoded_text` string (see below) — raw
QR *images* go through the same ignored-media path unless decoded first.

## Android QR Scanning (on-device, no cloud call)

QR payloads are decoded **on the phone**, never uploaded as an image. Use
Google ML Kit's Barcode Scanning API in **unbundled** mode — the model is
downloaded/managed by Google Play Services instead of shipping inside the
APK, which is what keeps the app lightweight:

```kotlin
// build.gradle.kts
dependencies {
    // Unbundled: ~200 KB added to APK, ~5 MB runtime (managed by Play Services)
    // vs. ~2.4 MB APK size for the bundled model.
    implementation("com.google.android.gms:play-services-mlkit-barcode-scanning:18.3.1")
}
```

The decoded text is what you send to the backend as
`metadata["qr_decoded_text"]` (see `SMSAnalyzer.analyze(text, metadata)`).
The Flask web UI (`src/app.py`) additionally supports uploading a QR image
directly for desktop/browser demo purposes — it decodes server-side with
OpenCV (`src/qr_decoder.py`, classical `cv2.QRCodeDetector`, no extra model
weight) and fills the same field, purely so you can test the pipeline
without a phone.

## Cluster Labels (15 fraud categories)

`direct_apk_download` · `qr_code_sideload_scam` · `banking_kyc_phishing` ·
`upi_payment_fraud` · `govt_challan_apk` · `delivery_parcel_fraud` ·
`loan_app_fraud` · `electricity_utility_fraud` · `aadhaar_pan_kyc_fraud` ·
`lottery_reward_cashback_scam` · `callback_vishing_fraud` ·
`investment_trading_fraud` · `telecom_recharge_fraud` ·
`job_recruitment_fraud` · `other_spam`



## Files

```
sms_spam_detector/
├── configs/config.yaml          ← all hyperparameters (aux_feature_dim=29)
├── src/
│   ├── feature_extractor.py     ← Stage 1: 29 heuristic signals
│   ├── inference.py             ← Stage 2: hard rules + Indic-BERT
│   ├── llm_analysis.py          ← Stage 3: LLM threat report
│   ├── url_intelligence.py      ← URL stages 2/3/4
│   ├── cluster_spam.py          ← fraud-type clustering
│   ├── app.py                   ← Android-friendly web UI
│   ├── train.py                 ← versioned training + optimal threshold
│   ├── evaluate.py              ← metrics on test sets
│   ├── quantize.py              ← INT8 quantization for mobile
│   ├── model.py                 ← Indic-BERT hybrid architecture
│   ├── data_preparation.py      ← dataset preparation
│   └── synthetic_data.py        ← synthetic data generation
├── scripts/train.sh             ← full SLURM pipeline
├── data/
│   ├── raw/                     ← raw CSV datasets
│   ├── processed/               ← HuggingFace dataset
│   ├── clusters/                ← per-fraud-type CSVs
│   └── blocklist/malicious_domains.txt   ← optional domain blocklist
└── requirements.txt
```
