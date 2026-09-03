# SurakshaSathi (सुरक्षा साथी)

SurakshaSathi ("Security Companion") is an end-to-end, production-grade mobile
security and cybercrime prevention solution designed for digital banking
customers. It protects users against fake mobile banking applications (e.g.
spoofed YONO SBI apps), malicious APKs, SMS/WhatsApp/Telegram phishing, credential/OTP theft.

## Table of Content

* [System Overview](#system-overview)
* [End-to-End System Architecture](#end-to-end-system-architecture)
* [Repository Modules](#repository-modules)
* [Key Platform Capabilities](#key-platform-capabilities)
* [Prerequisites](#prerequisites)
* [Comprehensive Developer Setup](#comprehensive-developer-setup)
* [1. Backend Setup (`Rag_model`)](#1-backend-setup-rag_model)
* [2. ML Spam Detector Setup (`sms_spam_detector_v2`)](#2-ml-spam-detector-setup-sms_spam_detector_v2)
* [3. Android Client Setup (`SurakshaSathi`)](#3-android-client-setup-surakshasathi)


* [Environment Variables & Config Guide](#environment-variables--config-guide)
* [Security, Privacy & DPDP Compliance](#security-privacy--dpdp-compliance)
* [Testing & Quality Assurance](#testing--quality-assurance)
* [Contributing & License](#contributing--license)

---

## System Overview

Fraudsters routinely circulate fake banking APKs and malicious URLs via SMS, WhatsApp, and Telegram, tricking users into revealing sensitive credentials, PINs, and OTPs. SurakshaSathi provides a multi-layered defense matrix:

1. **On-Device Detection**: Real-time SMS and notification scanning powered by a hybrid rule engine and TFLite ML classifier.
2. **Cloud RAG Intelligence**: Deep context analysis powered by Groq LLM (Llama 3.3 70B) with automated PII redaction and multi-lingual persona-tailored safety guidelines.
3. **3-Tier APK Security**: Instant package signature/hash verification followed by VirusTotal threat intelligence and MaMaDroid deep static call-graph analysis.
4. **Adaptive Friction & Biometrics**: Dynamic friction escalations including Hold-to-Confirm elements, biometric challenges, and ML Kit Face Liveness checks when high-risk actions occur.
5. **Cybercrime Reporting**: One-tap, tamper-evident forensic reporting to the National Cyber Crime Reporting Portal (I4C/NCRP) equipped with offline retry queues.

---

## End-to-End System Architecture

---

## Repository Modules

This repository is structured as a monorepo containing all core components of the SurakshaSathi ecosystem:

| Directory | Module Description | Technical Stack | Documentation |
| --- | --- | --- | --- |
| `SurakshaSathi/` | Production Android Client Application | Kotlin 2.0, Jetpack Compose, Hilt, Room, SQLCipher, TFLite | [Android README](SurakshaSathi/README.md) |
| `Rag_model/` | RAG AI Backend, Threat Intel & REST API | Python 3.11, FastAPI, Groq LLM, pgvector, Redis, Presidio | [Backend README](Rag_model/README.md) |
| `sms_spam_detector_v2/` | ML Model Training & Feature Extraction Scripts | Python, PyTorch, Scikit-Learn, TFLite Converter | [ML README](sms_spam_detector_v2/README.md) |

---

## Key Platform Capabilities

* **Multi-Channel Message Ingestion**: Captures SMS notifications via default SMS handlers or notification listeners, as well as OTT app messages from platforms like WhatsApp and Telegram.
* **Sub-Second On-Device Inference**: Runs TFLite spam models and TRAI DLT header validation without requiring network latency for initial triage.
* **Localized Persona-Based Guidelines**: RAG agent generates clear advice tailored for Farmers, Students, Seniors, or General Banking customers in English, Hindi, Marathi, Tamil, and other regional languages.
* **Zero-Trust APK Verification**: Validates application package certificates against official bank allowlists and flags side-loaded counterfeit banking apps.
* **Adaptive Intent Friction**: Intercepts high-risk user prompts with hold-to-confirm timers, biometric challenges, and facial liveness verification.
* **Fraud Geo-Dashboard & FCM Segment Alerts**: Visualizes active fraud campaigns on interactive maps and dispatches targeted push notifications to affected regions.

---

## Prerequisites

Ensure your development environment meets the following requirements:

**Mobile Client Development**

* **JDK**: OpenJDK 17 or higher
* **Android Studio**: Ladybug / Hedgehog (2023.1+) or newer
* **Android SDK**: API Level 35 (`compileSdk`), minimum support API 26 (Android 8.0+)
* **Gradle**: 8.7+ (Wrapper included)

**Backend & ML Engine**

* **Python**: 3.11+
* **Database**: PostgreSQL 14+ with `pgvector` extension enabled
* **Cache**: Redis 7+
* **API Keys**: Groq Cloud API Key, VirusTotal API Key (Optional), Google Translate Key (Optional)

---

## Comprehensive Developer Setup

Follow these steps to set up and run the full SurakshaSathi platform locally.

### 1. Backend Setup (`Rag_model`)

```bash
cd Rag_model

python -m venv .venv
# On Windows:
.venv\Scripts\activate
# On Linux/macOS:
source .venv/bin/activate

pip install --upgrade pip
pip install -r requirements.txt
python -m spacy download en_core_web_sm

cp .env.example .env
# Edit .env and supply POSTGRES_DSN and GROQ_API_KEY

uvicorn api.main:app --reload --host 0.0.0.0 --port 8000

```

Verify backend health by navigating to `http://localhost:8000/healthz` or viewing docs at `http://localhost:8000/docs`.

---

### 2. ML Spam Detector Setup (`sms_spam_detector_v2`)

If you want to re-train the NLP model or export updated feature keyword configurations:

```bash
cd sms_spam_detector_v2

python src/_export_keywords_json.py

python scripts/generate_feature_extractor_fixtures.py

```

Exported artifacts (`feature_extractor_keywords.json`) are automatically synced to `SurakshaSathi/app/src/main/assets/`.

---

### 3. Android Client Setup (`SurakshaSathi`)

```bash
cd SurakshaSathi

cp local.properties.example local.properties

# Open local.properties and update the backend URL:
# BACKEND_BASE_URL=http://10.0.2.2:8000/   (For Android Emulator pointing to local FastAPI)

./gradlew assembleNotificationOnlyDebug

./gradlew installNotificationOnlyDebug

```

---

## Environment Variables & Config Guide

### Backend Config (`Rag_model/.env`)

Copy `.env.example` to `.env`. Key parameters include:

| Variable | Required | Description |
| --- | --- | --- |
| `POSTGRES_DSN` | **Yes** | PostgreSQL connection string (`postgresql://user:pass@localhost:5432/surakshasathi`) |
| `GROQ_API_KEY` | **Yes** | Groq API key for Llama 3.3 70B inference |
| `REDIS_URL` | No | Redis connection URL for bloom filter & threat cache |
| `VIRUSTOTAL_API_KEY` | No | VirusTotal API key for Tier-2 APK scanning |
| `GOOGLE_TRANSLATE_API_KEY` | No | Google Translation API key for Indic translations |
| `FIREBASE_CREDENTIALS_PATH` | No | Path to Firebase Admin SDK JSON for FCM push alerts |

### Android Config (`SurakshaSathi/local.properties`)

Copy `local.properties.example` to `local.properties`. Key parameters include:

| Variable | Default | Description |
| --- | --- | --- |
| `BACKEND_BASE_URL` | `[https://api.surakshasathi.bank.example/](https://api.surakshasathi.bank.example/)` | Base URL of deployed `Rag_model` API |
| `MAPS_API_KEY` | `""` | Google Maps Android SDK Key (falls back to Canvas map if empty) |
| `VIRUSTOTAL_API_KEY` | `""` | On-device VirusTotal scan key |
| `SMS_STRATEGY` | `NOTIFICATION_ONLY` | Ingestion mode (`NOTIFICATION_ONLY` or `DEFAULT_HANDLER`) |
| `USE_OFFLINE_FALLBACK` | `false` | Enable offline mock data fallback for UI testing |

---

## Security, Privacy & DPDP Compliance

* **Data Minimization & Encryption**: All local data stored in Room is encrypted via SQLCipher using keys derived from the Android Keystore.
* **On-Device PII Protection**: User messages are sanitized on-device and further scrubbed via Microsoft Presidio before reaching external LLMs.
* **DPDP Compliance Ledger**: Built-in consent tracking (`UserPreferencesDataStore`) records explicit timestamped user permissions.
* **Auto-Purge Lifecycle**: Scanned message content is automatically purged after 7 days, while transaction audit logs are retained up to 30 days.

---

## Testing & Quality Assurance

### Android Unit Tests & Static Analysis

```bash
cd SurakshaSathi

# Run pure logic unit tests (Hybrid Decision Engine, DLT Rule Engine, Feature Extractor)
./gradlew testNotificationOnlyDebugUnitTest

# Run code style & lint checks
./gradlew ktlintCheck detekt

```

### Backend Test Suite

```bash
cd Rag_model

# Run backend unit & API endpoint tests
pytest

```

---

## Contributing & License

Contributions are welcome! Please ensure all code passes static analysis and unit tests before opening a pull request.

* Developed for Bank Cyber-Security & Anti-Phishing Initiatives.
* Distributed under the MIT License. See [LICENSE](LICENSE) for details.
