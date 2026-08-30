"""Shared pydantic models. These mirror the Mongo (Blue DB) collections and the Groq LLM
structured-output contract, so every module imports the same shape instead of re-declaring it."""

from datetime import datetime
from enum import Enum
from typing import Optional

from pydantic import BaseModel, Field


class Channel(str, Enum):
    sms = "sms"
    whatsapp = "whatsapp"
    telegram = "telegram"


class ContentType(str, Enum):
    text_only = "text_only"
    apk = "apk"
    url = "url"
    qr = "qr"
    callback = "callback"


# ---- Blue DB: `messages` collection ----
# One document per spam-classified message ingested from the Android client.
class MessageRecord(BaseModel):
    message_id: str
    original_message: str
    language: str                      # ISO 639-1 / BCP-47 tag as detected
    message_english: Optional[str] = None   # None until translated
    needs_translation: bool = True
    channel: Channel = Channel.sms
    content_type: ContentType = ContentType.text_only
    region: Optional[str] = None        # normalized state/city, not raw lat-long
    # message_type + region + persona are the clustering signal (see
    # clustering/run_clustering.py::build_feature_vector). persona is denormalized from the
    # reporting user's UserMap (set once at app install) and also recorded per-cluster as a
    # majority vote for alerting/targeting.
    persona: Optional[str] = None       # see clustering/persona.py for the fixed category set
    message_type: Optional[str] = None  # see clustering/message_type.py
    sender: Optional[str] = None        # the fraud message's spoofed originator, NOT the recipient
    # Recipient identity -- who reported/received this message. Real installs populate this from
    # the reporting device's UserMap entry; synthetic data populates it via
    # ingestion/enrich_blue_db_metadata.py::_synth_recipient. This is how alerting/daily_job.py
    # knows concretely who to notify once a cluster crosses its threshold (see
    # db/seed/seed_blue_db.py::seed_users, which derives UserMap rows from these two columns).
    user_name: Optional[str] = None
    user_phone: Optional[str] = None
    cluster_id: Optional[str] = None
    timestamp: datetime
    ml_model_metadata: Optional[dict] = None  # from Android client, online-only, never persisted long-term


# ---- Blue DB: `clusters` collection ----
# One document per DenStream micro-cluster / DBSCAN macro-cluster.
class ClusterRecord(BaseModel):
    cluster_id: str
    cluster_type: str                   # "micro" | "macro"
    parent_macro_cluster_id: Optional[str] = None
    centroid: list[float]               # PCA-reduced, region-weighted vector
    weight: float                       # point count (no decay applied, per spec)
    radius: float
    sample_message: str                 # representative message text
    sample_embedding: list[float]
    region: Optional[str] = None
    persona: Optional[str] = None       # majority persona among member messages
    fraud_type: Optional[str] = None    # majority message_type among member messages
    retrieved_docs: list[str] = Field(default_factory=list)     # doc IDs from the guideline vector store
    docs_retrieved_at: Optional[datetime] = None
    created_at: datetime
    updated_at: datetime


# ---- Blue DB: `user_map` collection ----
class UserMapRecord(BaseModel):
    """Set once at app install (region + persona) -- these two fields get denormalized onto
    every MessageRecord this user reports, and are the clustering signal (with message_type)."""
    user_id: str
    display_name: Optional[str] = None    # real installs: user-provided display name, not legal name
    phone_number: Optional[str] = None    # E.164-ish; needed so alerting/daily_job.py has a concrete
    # recipient per (region, persona) cluster to notify, not just an anonymous count. Real installs
    # populate this at consent-gated onboarding, same as any other PII (see privacy/pii_redaction.py
    # for the redaction discipline applied to free-text fields elsewhere in this service).
    region: Optional[str] = None
    persona: Optional[str] = None  # see clustering/persona.py for the fixed category set
    # Collected on the Android app's Registration screen (api/routes/user_registration.py) --
    # optional because synthetic/seeded rows (db/seed/seed_blue_db.py) never had these.
    email: Optional[str] = None
    preferred_language: Optional[str] = None  # BCP-47-ish tag: en, hi, mr, ta, ...
    lat: Optional[float] = None
    lon: Optional[float] = None
    is_vulnerable_group: bool = False
    metadata: dict = Field(default_factory=dict)


# ---- Blue DB: `national_digest` collection (cybercrime.gov.in scrape) ----
class NationalDigestRecord(BaseModel):
    date: datetime
    region: str
    fraud_type: Optional[str] = None
    report_count: int
    source: str = "cybercrime.gov.in"


# ---- Blue DB: `threat_intel_urls` collection (OpenPhish feed) ----
class ThreatIntelUrlRecord(BaseModel):
    url_hash: str
    url: str
    first_seen: datetime
    source: str = "openphish"


# ---- RAG input contract (Output 1, per-message) ----
class RagQueryInput(BaseModel):
    message_id: str
    query_message_english: str
    query_embedding: list[float]
    retrieved_docs: list[str]
    ml_model_metadata: Optional[dict] = None   # online-only, from Android client
    doc_fetch_flag: bool                        # True = fetch fresh docs, False = reuse cluster's cached docs


# ---- Groq structured-output contract (both Output 1 and Output 2 use this shape) ----
class Verdict(str, Enum):
    block = "BLOCK"
    quarantine = "QUARANTINE"
    allow = "ALLOW"


class TechnicalEvidence(BaseModel):
    resolved_destination: Optional[str] = None
    domain_creation_age: Optional[str] = None
    quishing_anomaly_detected: bool = False
    callback_number_verified: bool = False
    # Populated from url_qr_callback_engine.url_pipeline.analyze_url's UrlAnalysisResult, which
    # already computes these -- previously dropped on the floor before reaching this schema, so
    # the Android client's "safe simulation" sandbox had no fake-domain/lookalike-app signal to
    # show even though the backend had already done the work.
    domain_age_days: Optional[int] = None
    is_pwa: bool = False
    pwa_name_spoofing_suspected: bool = False


class ThreatReport(BaseModel):
    verdict: Verdict
    threat_type: str
    risk_score: float = Field(ge=0, le=100)
    suspicious_signals: list[str]
    technical_evidence: TechnicalEvidence
    plain_language_explanation: str
    recommended_action: str
    # Short label naming the specific fraud pattern matched (e.g. "fake KYC-block urgency +
    # non-bank link"), grounded in the retrieved guideline docs -- drives the adaptive friction
    # engine's Layer B micro-education screen title.
    pattern_matched: str = ""
    # The Layer B micro-education body: a real 1-1.5 paragraph explanation (which pattern this
    # is, why it works on people, a verified counter-fact, what to do instead), NOT a one-liner --
    # generated in this same call/schema so the friction engine's education step costs zero
    # additional LLM round trips. See llm/prompt_templates.SYSTEM_PROMPT for the length/grounding
    # instruction.
    micro_lesson: str = ""
    # BCP-47-ish tag (hi, mr, ta, en, ...) the two fields above are actually written in --
    # the LLM is instructed (see llm/prompt_templates.build_per_message_prompt) to localize
    # plain_language_explanation/recommended_action into this language, not just detect it.
    language: str = "en"
