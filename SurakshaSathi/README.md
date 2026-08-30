# SurakshaSathi — Android Client

**SurakshaSathi** ("Security Companion") is an Android application that
protects users from banking fraud via on-device SMS/APK scanning,
multi-language phishing warnings, adaptive friction, and automated fraud
reporting.

> This repository is the **Android client only**. The Python RAG backend lives
> at [`Rag_model/`](../Rag_model/README.md).

---

## Table of Contents

1. [Prerequisites](#1-prerequisites)
2. [Quick Start](#2-quick-start)
3. [Configuration Reference](#3-configuration-reference)
4. [Build Variants](#4-build-variants)
5. [Running the App](#5-running-the-app)
6. [Running Tests](#6-running-tests)
7. [Project Architecture](#7-project-architecture)
8. [Feature Map](#8-feature-map)
9. [Backend API Contracts](#9-backend-api-contracts)
10. [Security & Privacy](#10-security--privacy)
11. [Known Limitations](#11-known-limitations)

---

## 1. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **JDK** | 17 | `java -version` must show 17.x |
| **Android Studio** | Hedgehog (2023.1) or newer | Includes the Gradle wrapper |
| **Android SDK** | API 35 (compileSdk) | Install via SDK Manager |
| **Android SDK Platform Tools** | Latest | For `adb` |
| **Min device/emulator** | API 26 (Android 8.0) | `minSdk = 26` |

> **Windows users:** The project is configured to cache Gradle and Android SDK on drive `D:\` by default (see `gradle.properties`). If your SDK is elsewhere, override `ANDROID_HOME` in `local.properties`.

---

## 2. Quick Start

```bash
# 1. Clone the repository
git clone https://github.com/sharedmem/SurakshaSathi.git
cd SurakshaSathi

# 2. Copy the config template
cp local.properties.example local.properties

# 3. Edit local.properties and fill in required values (see §3)
#    At minimum, set BACKEND_BASE_URL
notepad local.properties   # Windows
# nano local.properties    # macOS/Linux

# 4. Build the Play-publishable debug variant
./gradlew assembleNotificationOnlyDebug

# 5. Install on a connected device / running emulator
./gradlew installNotificationOnlyDebug
```

The APK will be output to:
```
app/build/outputs/apk/notificationOnly/debug/app-notificationOnly-debug.apk
```

---

## 3. Configuration Reference

All configuration is supplied via `local.properties` (gitignored — never commit it).
Copy [`local.properties.example`](local.properties.example) as your starting point.

### Required

| Key | Description | Where to get it |
|---|---|---|
| `BACKEND_BASE_URL` | Base URL for the SurakshaSathi RAG backend (trailing `/` required) | Deploy the `Rag_model/` service — see its README |

### Optional (graceful fallback if missing)

| Key | Feature | Fallback behaviour |
|---|---|---|
| `MAPS_API_KEY` | Flow 4a: fraud heatmap | Static Compose Canvas heatmap over India |
| `VIRUSTOTAL_API_KEY` | Flow 2: APK cloud scan Tier 2 | Skips VirusTotal, uses local hash-matching only |
| `AZURE_TRANSLATOR_KEY` | Multi-language lesson content | Serves English only |
| `AZURE_TRANSLATOR_REGION` | Azure Translator region | Required if key is set |
| `AZURE_TRANSLATOR_ENDPOINT` | Translator endpoint | Defaults to `https://api.cognitive.microsofttranslator.com/` |
| `SMS_STRATEGY` | SMS ingestion mode | Defaults to `NOTIFICATION_ONLY` |
| `USE_OFFLINE_FALLBACK` | Skip real API calls | Defaults to `false` |

### Obtaining API keys

- **Google Maps** → [Google Cloud Console](https://console.cloud.google.com/) → APIs & Services → Maps SDK for Android
- **VirusTotal** → [virustotal.com/gui/my-apikey](https://www.virustotal.com/gui/my-apikey)
- **Azure Translator** → [portal.azure.com](https://portal.azure.com/) → Create resource → Translator

---

## 4. Build Variants

The project has **two product flavors** to handle Play Store's restricted SMS permissions:

| Flavor | Gradle task | Permissions | Use case |
|---|---|---|---|
| `notificationOnly` *(default)* | `assembleNotificationOnlyDebug` | No restricted permissions — reads SMS via `NotificationListenerService` (same mechanism as WhatsApp/Telegram) | **Play Store distribution** — no declaration form needed |
| `defaultHandler` | `assembleDefaultHandlerDebug` | `READ_SMS` + `RECEIVE_SMS` | Enterprise/MDM sideload, or after Play's Permissions Declaration Form is approved |

The flavor also drives `BuildConfig.SMS_STRATEGY`, keeping runtime behavior and manifest permissions in sync.

---

## 5. Running the App

### On an emulator
```bash
# Start an AVD (API 26+), then:
./gradlew installNotificationOnlyDebug
```

### On a physical device
```bash
adb devices              # confirm device is listed
./gradlew installNotificationOnlyDebug
```

### First launch flow
1. **Splash** → checks onboarding state
2. **Onboarding** → language selection, feature overview
3. **Registration** → user profile setup (phone number, region, persona)
4. **Permissions** → camera, notification listener, location (optional)
5. **Home Hub** → main dashboard with all features

---

## 6. Running Tests

```bash
# Unit tests (JUnit 5 + MockK)
./gradlew testNotificationOnlyDebugUnitTest

# Static analysis
./gradlew ktlintCheck
./gradlew detekt

# All checks together
./gradlew check
```

**34 unit tests** cover the security-critical logic:
- `HybridDecisionEngine` — spam/phishing verdict fusion
- `RuleBasedClassifier` — 7 heuristic fraud rules (DLT sender, OTP extraction, urgency patterns)
- `TraiDltValidator` — TRAI sender ID registration check
- `LocalBehaviorRuleEngine` — adaptive friction rules (R01–R07)
- `ImpersonationChecker` — fake-app cert fingerprint detection
- `WeightedRiskScoringEngine` — behavioral biometric risk scoring
- `EvaluateFrictionUseCase` — friction level decision (NONE → PIN → BIOMETRIC → LIVENESS)

---

## 7. Project Architecture

Clean Architecture with feature-based package layout (single Gradle module):

```
com.sbi.surakshasathi/
├── app/                          Application, MainActivity, NavHost
│   ├── navigation/               Screen sealed class, SurakshaSathiNavHost
│   ├── presentation/             Splash, Onboarding, Registration, Permissions, HomeHub
│   └── service/                  FCM service
├── core/
│   ├── common/                   Result<T>, AppError, DispatcherProvider
│   ├── designsystem/             Compose theme (Material 3), shared components
│   ├── database/                 AppDatabase (Room + SQLCipher)
│   ├── datastore/                Encrypted preferences + DPDP consent ledger
│   ├── network/                  Retrofit/OkHttp, cert pinning, auth interceptor
│   ├── notification/             Notification channel registry
│   ├── translation/              Azure Translator client + cache
│   ├── tts/                      Text-to-speech controller
│   └── di/                       Core Hilt modules
└── feature/
    ├── messagescan/              Flow 1  — SMS/WhatsApp/Telegram scanning
    ├── ragwarning/               Flow 1b — RAG backend warning display
    ├── apkscan/                  Flow 2  — APK/URL 3-tier scanning
    ├── adaptivefriction/         Flow 3  — Behavioral biometrics + liveness
    ├── messagefriction/          Flow 3b — Message-level friction controls
    ├── frauddashboard/           Flow 4a — Fraud heatmap + segment alerts
    ├── ncrpreport/               Flow 4b — I4C/NCRP forensic report submission
    ├── awareness/                Flow 5  — Lessons, quizzes, badges, QR verifier
    └── userprofile/              User registration & profile management
```

**Layer rule:** `presentation → domain ← data`. Domain packages have zero Android/framework imports.

---

## 8. Feature Map

| Flow | Feature | What it does |
|---|---|---|
| **1** | Message Scan | Intercepts SMS/WhatsApp/Telegram notifications; runs on-device TFLite + rule-based hybrid classifier |
| **1b** | RAG Warning | Sends suspicious messages to the backend RAG agent; displays localized plain-language warnings with guidance |
| **2** | APK Scan | 3-tier scanning: on-device hash+branding (Tier 1) → VirusTotal (Tier 2) → MaMaDroid deep analysis (Tier 3, server-side) |
| **3** | Adaptive Friction | Behavioral biometrics (touch dynamics, keystroke timing, device motion); liveness challenge (blink + head turn via ML Kit) |
| **3b** | Message Friction | Hold-to-confirm UI on high-risk outgoing actions |
| **4a** | Fraud Dashboard | Real-time fraud heatmap (Google Maps); FCM segment alerts by region/persona |
| **4b** | NCRP Report | Forensic report with WorkManager guaranteed delivery; provisional case ID while offline |
| **5** | Awareness Hub | Cybersecurity lessons with quizzes, badge system, QR code verifier, nudge notifications |

---

## 9. Backend API Contracts

All endpoints are relative to `BACKEND_BASE_URL`. These Retrofit clients are already implemented — the backend team can implement to these contracts directly.

### RAG agent (Flow 1b)
```
POST /rag/analyze
Body:  { message, metadata: { sender, source, receivedAt, device: { model, osVersion, locale }, extractedUrls } }
→     { verdict: "PHISHING"|"SCAM"|"SAFE", warning, guideline, language, persona, confidence }

GET  /rag/segment-alerts?region=&persona=
→    [ { region, persona, language, title, body } ]

POST /rag/feed   (fire-and-forget training signal from NCRP reports)
Body: { apkSha256, messageBody, region, quarantine: true }
```

### Threat intelligence (Flow 2)
```
POST /threat/apk         { sha256, packageName, signingCertSha256[] } → { verdict, engineHits }
POST /threat/apk/deep    (same request, MaMaDroid)                    → { verdict, mamaDroidScore }
GET  /threat/url?u=                                                    → { verdict, isKnownPhishingDomain }
GET  /threat/bank-allowlist                                            → [ { packageName, signingCertSha256 } ]
POST /integrity/verify   { integrityToken }                            → { status }
```
`verdict` is always `GOODWARE | MALWARE | UNKNOWN`.

### Fraud dashboard (Flow 4a)
```
GET  /fraud/aggregate                                          → [ { lat, lng, weight, persona, region, campaignTag } ]
POST /alerts/segment  { region, persona, language, message }   → dispatched via FCM
```

### NCRP reporting (Flow 4b)
```
POST /i4c/ncrp/report
Body: { apkSha256, installSource, deviceIntegrity, offendingMessage, location, reportedAtMillis, reporterConsent }
→    { caseId, status }
```

### Awareness content (Flow 5)
```
GET /awareness/lessons?lang=
→  [ { id, title, description, language, quiz: [{ question, options, correctOptionIndex }], badgeIdOnCompletion } ]
```
Fallback: three bundled English lessons (`BundledLessons.kt`) render when unreachable.

---

## 10. Security & Privacy

| Control | Implementation |
|---|---|
| **Encryption at rest** | Room encrypted via SQLCipher; key derived from Android Keystore (`DatabaseKeyManager`) |
| **Encryption in transit** | TLS only; cleartext disabled; cert pinning wired (activate by replacing placeholder pins in `NetworkSecurityConfig`) |
| **DPDP consent ledger** | `UserPreferencesDataStore` records what was consented to and when |
| **Data minimization** | Message bodies purged after 7 days; full rows after 30 days; threat-hash cache capped at 10k entries; NCRP reports retained 90 days (evidentiary) |
| **No PII in logs** | Timber planted in debug builds only; release builds never log |
| **Play Integrity** | On-device verdict forwarded to `/integrity/verify` for server-side decoding (never trusted alone) |
| **Signing cert verification** | Replace `REPLACE_WITH_REAL_*_SIGNING_CERT_SHA256` in `BankAllowList.kt` with real fingerprints before release |

---

## 11. Known Limitations

- **WhatsApp/Telegram**: notification text only (no message history, no media) — required by platform policy
- **Liveness**: blink + head-turn detection via ML Kit; not true depth-based anti-spoofing (most devices lack a depth sensor)
- **FCM topic subscription**: the receive path is fully wired; the subscribe step (asking user persona/region and calling `subscribeToTopic`) is not yet built
- **TFLite model**: `assets/spam_classifier.tflite` is a placeholder; swap in a real trained model with the same I/O contract (`int32[1,128]` → `float32[1,1]`) — zero code change required
- **Certificate pinning**: wired but inactive until real backend cert pins replace the placeholders
- **Bank signing certs**: `BankAllowList.kt` contains placeholder SHA-256 hashes — replace with real values per bank before release
- **MaMaDroid (Tier 3 APK scan)**: server-side contract only; the client never runs call-graph extraction itself
