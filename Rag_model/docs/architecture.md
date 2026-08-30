# SurakshaSathi RAG Backend -- Architecture

Scope: this directory only. The Android client (separate repo/folder, `SurakshaSathi/`) calls
this backend as an API; nothing here touches the client.

## Data flow

```
Android on-device classifier flags a message as spam
        |
        v
POST /v1/scan/message (api/routes/message_scan.py)
        |
        +-- language/detect.py + translate.py  (Google Translate; falls back to raw text if down)
        |
        +-- url_qr_callback_engine/l1_triage.py  (regex tokenize + in-process Bloom filter check)
        |         |
        |         +-- bloom hit -----------------------> skip to LLM, high-confidence flag
        |         +-- URL found  --> url_pipeline.py     (HEAD redirect chase, WHOIS, PWA audit)
        |         +-- callback   --> callback_pipeline.py (DLT heuristic, Twilio, lure keywords)
        |         +-- QR image   --> qr_pipeline.py       (ALFA/FAST reconstruction, feature extract)
        |
        +-- db/vector_store.py semantic_search   (guideline docs, skipped if Postgres not set)
        +-- live web corroboration (Tavily, <1.2s budget, best-effort)
        |
        v
llm/groq_client.py  (Llama-3.3-70B, JSON-object mode + schema-in-prompt + pydantic validation)
        |
        v
ThreatReport returned to app; message persisted to Blue DB async (after response is sent)
```

APK payloads go through a separate endpoint (`POST /v1/scan/apk`) running Lane A (signature/
VirusTotal) and Lane B (manifest/permission scan; DenseNet bytecode model not yet trained)
concurrently, arbitrated by `apk_engine/fusion_core.py`'s 4-case matrix.

Batch/offline side (not on the request path): `ingestion/run_clustering.py` (DenStream micro- +
DBSCAN macro-clustering), `crawlers/*` (OpenPhish every 12h, cybercrime.gov.in digest daily
~06:00 IST, RBI FAME weekly), `alerting/daily_job.py` (07:00 IST: threshold alerts + heatmap
digest via FCM). These run as separate Cloud Run Jobs, not HTTP routes -- a 25s APK scan or a
DBSCAN pass must never share timeout/autoscaling config with the sub-2s message-scan path.

## Key decisions made along the way (and why)

- **Cloud SQL for PostgreSQL (pgvector) instead of MongoDB + Pinecone**: one GCP-native instance
  for both Blue DB and the guidelines/case-notes vector store, avoiding a cross-cloud hop on both
  the hot path and the batch path. Memorystore for Redis for hot-path caching.
- **Pub/Sub instead of Kafka** for the NCRP/telemetry/case-notes ingestion topic: same decoupling,
  no cluster to run, native Cloud Run integration.
- **DenStream micro-clustering with NO fading/decay** (explicit requirement): clusters only grow,
  never shrink for going quiet. Region-weighted (lat/lon scaled 0-1, x2.5 multiplier) so identical
  fraud text in different regions still lands in separate clusters.
- **Multilingual sentence-transformer (`paraphrase-multilingual-MiniLM-L12-v2`) for clustering
  embeddings**, chosen over requiring translation-then-embed, since it natively handles Hindi/
  Tamil/Telugu/Bengali/etc. without a translation-API dependency in the clustering path.
- **Groq's `json_schema` strict mode isn't supported on llama-3.3-70b-versatile** (confirmed by a
  live 400 from the API) -- fell back to `json_object` mode with the schema spelled out in the
  system prompt, pydantic validation, and one retry on a validation failure.
- **PII redaction (Presidio + regex/checksum)** runs on NCRP/telemetry/case-notes text before
  storage, indexing, or any external LLM call -- Aadhaar via Verhoeff-checksum-validated regex,
  card numbers via Luhn, names/phones via Presidio. Does not run before technical fraud-signal
  extraction (HLR lookup, URL resolution needs the raw value first).
- **Everything that touches an optional dependency (Redis, Postgres, Translation API, WHOIS)
  degrades gracefully** rather than crashing the request -- verified live: a message with an
  unreachable synthetic phishing domain and a failed translation call both still produced a
  correct ThreatReport instead of a 500.

## Verified working end-to-end (live keys, real data, not simulated)

- Enriched your two real CSVs -> 6,999 spam-flagged messages with synthesized metadata (region
  weighted toward Mumbai/Delhi per spec, channel, sender, timestamps).
- Ran the full clustering pipeline on them: 2,850 micro-clusters -> 637 macro-clusters (campaigns),
  cleanly separating fraud types (Aadhaar-block, electricity-bill, OTP-phishing, bank-cashback,
  Income Tax refund, etc.) and regions.
- Parsed and chunked 21 of 23 SBIDocuments PDFs (2 are scanned images, need OCR) into 2,678 chunks,
  embedded with the same 384-dim encoder used for case notes.
- `POST /v1/scan/message` end-to-end against live Groq/Tavily/VirusTotal/Twilio/WhoisXML: tested
  with an English Aadhaar-phishing message and a Hindi one (translation currently down --
  Groq/Llama handled the Hindi text directly and still produced a correct verdict).

## What's still a stub (needs data/infra you don't have yet, not "not built")

- URL GBC / QR XGBoost / APK DenseNet classifiers -- need labeled training data (see
  docs/CREDENTIALS.md for recommended public bootstrap datasets).
- Postgres and Redis -- code is complete and tested against the schema, just needs the live
  instances (gcloud commands given in chat for Cloud SQL).
- Firebase Admin SDK service-account JSON for FCM sending.
- Google Cloud Translation API needs enabling in your GCP Console.

## Folder map

```
Rag_model/
  config/settings.py          all env-driven config
  common/                     shared pydantic schemas, retry/circuit-breaker wrapper
  language/                   detect + translate
  privacy/                    PII redaction (Presidio + regex/checksum)
  clustering/                 embedding, DenStream, macro-clustering, region weighting
  db/                         Postgres (Blue DB + pgvector) + Redis clients, seed script
  ingestion/                  CSV enrichment, clustering runner, PDF ingestion, case-notes pipeline
  crawlers/                   OpenPhish, cybercrime.gov.in, RBI FAME, Truecaller stub
  apk_engine/                 Lane A (signature/VT), Lane B (manifest scan + DenseNet stub), fusion core
  url_qr_callback_engine/     L1 triage, URL/PWA, QR/quishing, callback/vishing, SPARE handshake
  llm/                        Groq client, prompt templates, JSON schema
  alerting/                   daily 7am job, heatmap, FCM sender
  api/                        FastAPI app + routes (what the Android app actually calls)
  data/processed/             enriched/clustered CSVs, staged guideline embeddings (generated, gitignored-safe)
  docs/                       this file + CREDENTIALS.md
```
