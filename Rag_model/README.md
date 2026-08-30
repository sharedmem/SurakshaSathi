# SurakshaSathi RAG Backend

The Python backend powering the SurakshaSathi Android app. Provides real-time phishing detection, APK analysis, fraud aggregation, guardian chat, and push-notification alerting — all behind a FastAPI HTTP service.

> The Android client that consumes this API lives at [`SurakshaSathi/`](../SurakshaSathi/README.md).

---

## Table of Contents

1. [Overview](#1-overview)
2. [Prerequisites](#2-prerequisites)
3. [Quick Start (Local Development)](#3-quick-start-local-development)
4. [Environment Variables Reference](#4-environment-variables-reference)
5. [Project Structure](#5-project-structure)
6. [API Endpoints](#6-api-endpoints)
7. [Database Setup](#7-database-setup)
8. [Running in Production (Cloud Run)](#8-running-in-production-cloud-run)
9. [Key Dependencies](#9-key-dependencies)

---

## 1. Overview

The backend is built with **FastAPI** and runs as a single Cloud Run service for low-latency API calls. Background jobs (daily fraud clustering, APK deep-analysis, data crawling) run as **separate Cloud Run Jobs** so they don't share timeout/autoscaling config with the sub-1.5s message-scan path.

**What each component does:**

| Component | Description |
|---|---|
| `api/` | FastAPI entrypoint + all HTTP routes |
| `llm/` | Groq LLM client, prompt templates, RAG pipeline |
| `db/` | PostgreSQL + pgvector client (blue DB for embeddings + guidelines) |
| `clustering/` | DBSCAN fraud-pattern clustering (runs as a Cloud Run Job) |
| `alerting/` | FCM segment-alert dispatch |
| `apk_engine/` | APK static analysis via Androguard |
| `url_qr_callback_engine/` | URL reputation + QR code verification |
| `crawlers/` | Threat-intel data ingestion |
| `ingestion/` | Document/SMS ingestion into the vector store |
| `language/` | Language detection + translation (Google Cloud Translate / Bhashini) |
| `privacy/` | PII redaction (Presidio) before LLM calls |
| `common/` | Shared schemas and utilities |
| `config/` | Pydantic settings (reads from `.env`) |

---

## 2. Prerequisites

| Tool | Version | Notes |
|---|---|---|
| **Python** | 3.11+ | `python --version` |
| **PostgreSQL** | 14+ | With `pgvector` extension installed |
| **Redis** | 7+ | Optional but recommended for caching |
| **pip** | Latest | `pip install --upgrade pip` |

> **Cloud services used (optional for local dev):**
> - Google Cloud Translation API (fallback: English only)
> - Groq API (LLM for RAG pipeline)
> - VirusTotal API (APK cloud scanning)
> - Twilio (callback number verification)
> - Tavily (web corroboration)
> - Firebase Admin SDK (FCM push notifications)

---

## 3. Quick Start (Local Development)

```bash
# 1. Clone and enter the directory
git clone https://github.com/sharedmem/SurakshaSathi.git
cd SurakshaSathi/Rag_model

# 2. Create a virtual environment
python -m venv .venv
source .venv/bin/activate      # Linux/macOS
# .venv\Scripts\activate       # Windows PowerShell

# 3. Install dependencies
pip install --upgrade pip
pip install -r requirements.txt

# 4. Install spaCy English model (required by PII redaction)
python -m spacy download en_core_web_sm

# 5. Set up environment variables
cp .env.example .env
# Edit .env and fill in at minimum:
#   POSTGRES_DSN — your local PostgreSQL connection string
#   GROQ_API_KEY — from console.groq.com

# 6. Run database migrations / pgvector setup
#    (run your SQL init script or the setup in db/postgres_client.py)

# 7. Start the development server
uvicorn api.main:app --reload --host 0.0.0.0 --port 8000
```

The API will be available at `http://localhost:8000`.
Interactive docs: `http://localhost:8000/docs`
Health check: `http://localhost:8000/healthz`

---

## 4. Environment Variables Reference

Copy [`.env.example`](.env.example) to `.env` and fill in values. **Never commit `.env`** — it is gitignored.

### Required

| Variable | Description |
|---|---|
| `POSTGRES_DSN` | PostgreSQL connection string, e.g. `postgresql://user:pass@localhost:5432/surakshasathi` |
| `GROQ_API_KEY` | LLM inference key from [console.groq.com](https://console.groq.com/) |

### Optional — services degrade gracefully if missing

| Variable | Service | Fallback |
|---|---|---|
| `REDIS_URL` | Hot cache / bloom filter | In-memory dict (no persistence across restarts) |
| `PGVECTOR_DIM` | Embedding dimension | Defaults to `96` |
| `GROQ_MODEL` | LLM model name | Defaults to `llama-3.3-70b-versatile` |
| `VIRUSTOTAL_API_KEY` | APK cloud scan Tier 2 | Skips VirusTotal, returns `UNKNOWN` verdict |
| `GOOGLE_TRANSLATE_API_KEY` | Multi-language support | Returns English only |
| `BHASHINI_API_KEY` | Indic language fallback | Disabled; set `ENABLE_BHASHINI_FALLBACK=true` to activate |
| `TWILIO_ACCOUNT_SID` / `TWILIO_AUTH_TOKEN` | Callback number verification | Feature returns `UNVERIFIED` |
| `WHOISXML_API_KEY` | URL WHOIS lookup | Skips domain-age check |
| `TAVILY_API_KEY` | Real-time web corroboration | Skips web search in RAG pipeline |
| `FIREBASE_CREDENTIALS_PATH` | FCM push notifications | Alerts not dispatched |
| `WEB_SEARCH_TIMEOUT_MS` | Web search latency budget | Defaults to `1200` |
| `URL_HEAD_TIMEOUT_MS` | URL probe latency budget | Defaults to `800` |

### Getting API keys

| Service | URL |
|---|---|
| Groq | [console.groq.com](https://console.groq.com/) |
| VirusTotal | [virustotal.com/gui/my-apikey](https://www.virustotal.com/gui/my-apikey) |
| Google Cloud Translation | [console.cloud.google.com](https://console.cloud.google.com/) → APIs → Cloud Translation API |
| Twilio | [console.twilio.com](https://console.twilio.com/) |
| WhoisXML | [whoisxmlapi.com](https://www.whoisxmlapi.com/) |
| Tavily | [app.tavily.com](https://app.tavily.com/) |
| Firebase Admin SDK | Firebase Console → Project Settings → Service Accounts → Generate new private key |

---

## 5. Project Structure

```
Rag_model/
├── api/
│   ├── main.py                  FastAPI app entrypoint
│   └── routes/
│       ├── message_scan.py      POST /rag/analyze, POST /rag/feed, GET /rag/segment-alerts
│       ├── apk_scan.py          POST /threat/apk, POST /threat/apk/deep, GET /threat/url
│       ├── dashboard.py         GET /fraud/aggregate, POST /alerts/segment
│       ├── guardian_chat.py     POST /guardian/chat (conversational fraud guidance)
│       └── user_registration.py POST /user/register
├── llm/
│   ├── groq_client.py           Groq API client with retry logic
│   ├── prompt_templates.py      Persona-aware prompt templates (FARMER/STUDENT/SENIOR/GENERAL)
│   └── schema.py                LLM input/output Pydantic models
├── db/
│   └── postgres_client.py       SQLAlchemy + pgvector async client
├── clustering/                  DBSCAN fraud-pattern clustering (Cloud Run Job)
├── alerting/                    FCM segment-alert dispatch
├── apk_engine/                  Androguard-based APK static analysis
├── url_qr_callback_engine/      URL reputation + QR verification
├── crawlers/                    Threat-intel data crawlers
├── ingestion/                   Vector store ingestion pipeline
├── language/                    Language detection + translation
├── privacy/
│   └── pii_redaction.py         Presidio-based PII redaction before LLM calls
├── common/
│   └── schemas.py               Shared Pydantic models (MessageEnvelope, etc.)
├── config/
│   └── settings.py              Pydantic Settings (reads .env)
├── requirements.txt
└── .env.example                 Configuration template
```

---

## 6. API Endpoints

### Health
```
GET  /healthz          → { "status": "ok" }
```

### Message Scan — RAG pipeline (Flow 1b)
```
POST /rag/analyze
Body: {
  "message": "string",
  "metadata": {
    "sender": "string",
    "source": "SMS|WHATSAPP|TELEGRAM",
    "receivedAt": "ISO-8601",
    "device": { "model": "string", "osVersion": "string", "locale": "string" },
    "extractedUrls": ["string"]
  }
}
Response: {
  "verdict": "PHISHING|SCAM|SAFE",
  "warning": "string",
  "guideline": "string",
  "language": "string",
  "persona": "FARMER|STUDENT|SENIOR|GENERAL",
  "confidence": 0.0–1.0
}

GET  /rag/segment-alerts?region=MH&persona=FARMER
→    [ { "region", "persona", "language", "title", "body" } ]

POST /rag/feed   (fire-and-forget training signal from NCRP reports)
Body: { "apkSha256", "messageBody", "region", "quarantine": true }
```

### APK & URL Scanning (Flow 2)
```
POST /threat/apk       { sha256, packageName, signingCertSha256[] } → { verdict, engineHits }
POST /threat/apk/deep  (same body, MaMaDroid deep analysis)         → { verdict, mamaDroidScore }
GET  /threat/url?u=<url>                                            → { verdict, isKnownPhishingDomain }
GET  /threat/bank-allowlist                                         → [ { packageName, signingCertSha256 } ]
POST /integrity/verify { integrityToken }                           → { status }
```

### Fraud Dashboard (Flow 4a)
```
GET  /fraud/aggregate
→    [ { lat, lng, weight, persona, region, campaignTag } ]

POST /alerts/segment
Body: { region, persona, language, message }
→    dispatched via FCM topic "<persona>_<region>_<language>"
```

### Guardian Chat
```
POST /guardian/chat
Body: { message, sessionId, userId }
→    { reply, suggestedActions[] }
```

### User Registration
```
POST /user/register
Body: { phoneNumber, region, persona, language, consentTimestamp }
→    { userId, status }
```

---

## 7. Database Setup

The backend uses **PostgreSQL with the `pgvector` extension** for similarity search on message embeddings.

```sql
-- Run once on your PostgreSQL instance
CREATE EXTENSION IF NOT EXISTS vector;

-- The application creates tables automatically via SQLAlchemy on first startup.
-- For production, run the migration scripts in db/ manually.
```

For local development, a plain `postgresql://` DSN works. For Cloud Run production, the backend uses the **Cloud SQL Python Connector** (no public IP needed) — configure `POSTGRES_DSN` with the Cloud SQL instance connection name format.

---

## 8. Running in Production (Cloud Run)

```bash
# Build and push container image
gcloud builds submit --tag gcr.io/<PROJECT_ID>/surakshasathi-rag .

# Deploy to Cloud Run
gcloud run deploy surakshasathi-rag \
  --image gcr.io/<PROJECT_ID>/surakshasathi-rag \
  --platform managed \
  --region asia-south1 \
  --allow-unauthenticated \
  --set-secrets GROQ_API_KEY=groq-api-key:latest,POSTGRES_DSN=postgres-dsn:latest
```

> **Architecture note:** Keep clustering, crawlers, and APK deep-analysis as separate **Cloud Run Jobs** — they have 25s+ runtimes and must not share the autoscaling config of the sub-1.5s message-scan path.

---

## 9. Key Dependencies

| Package | Purpose |
|---|---|
| `fastapi` + `uvicorn` | HTTP server |
| `sqlalchemy` + `psycopg` + `pgvector` | PostgreSQL + vector similarity |
| `redis` | Hot cache + bloom filter |
| `sentence-transformers` | Message embeddings (96-dim) |
| `groq` | LLM inference (Llama 3.3 70B) |
| `presidio-analyzer` + `presidio-anonymizer` | PII redaction before LLM calls |
| `androguard` | APK static analysis |
| `langchain-text-splitters` | Document chunking for ingestion |
| `google-cloud-translate` | Multi-language support |
| `firebase-admin` | FCM push notifications |
| `tavily-python` | Real-time web corroboration |
| `httpx` | Async HTTP client |
