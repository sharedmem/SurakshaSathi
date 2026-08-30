"""Central runtime configuration. All external credentials/flags load from env (.env locally,
GCP Secret Manager -> env injection in Cloud Run). Nothing here should be hardcoded."""

from pydantic_settings import BaseSettings, SettingsConfigDict


class Settings(BaseSettings):
    model_config = SettingsConfigDict(env_file=".env", extra="ignore")

    # Blue DB + guidelines vector store, consolidated onto one Cloud SQL for PostgreSQL
    # instance (pgvector extension) -- GCP-native, avoids a cross-cloud hop to Atlas/Pinecone,
    # and one instance is enough at this corpus/message scale. See docs/DATASTORE_CHOICES.md.
    postgres_dsn: str = ""          # postgresql://user:pass@/dbname?host=/cloudsql/<instance-conn-name>
    pgvector_dim: int = 96          # must match clustering.embedding's reduced dim (text + region + persona + message_type)

    # Optional: point the `user_map` table (api/routes/user_registration.py) at a physically
    # separate database instance -- e.g. a dedicated Azure Database for PostgreSQL, if the user
    # records need to live outside GCP for organizational reasons. Empty by default, in which case
    # registration falls back to writing into the same `postgres_dsn` instance as everything else
    # (see _get_users_session_factory in that route module) -- no second instance is required for
    # the feature to work end-to-end.
    users_db_dsn: str = ""

    # Cache / bloom filter -- Memorystore for Redis (GCP-native managed Redis)
    redis_url: str = ""

    # LLM
    groq_api_key: str = ""
    groq_model: str = "llama-3.3-70b-versatile"

    # Reputation
    virustotal_api_key: str = ""

    # Language (Google Cloud Translation v2 REST API, key-based auth -- simpler than a service
    # account for this single API call, and matches the credential type actually available)
    google_translate_api_key: str = ""
    bhashini_api_key: str = ""
    enable_bhashini_fallback: bool = False

    # Callback verification
    twilio_account_sid: str = ""
    twilio_auth_token: str = ""

    # URL reputation
    whoisxml_api_key: str = ""

    # Web corroboration
    tavily_api_key: str = ""

    # Push
    firebase_credentials_path: str = ""

    # Feature flags
    enable_truecaller: bool = False

    # Latency budgets (ms)
    web_search_timeout_ms: int = 1200
    url_head_timeout_ms: int = 800

    # Clustering -- region + message type + persona are the clustering signal, with text embedding
    # as a secondary refinement within those buckets. region/message_type weights are deliberately
    # larger than 1.0 so they dominate the raw text-PCA distance rather than being drowned out by
    # it.
    #
    # Persona history: it was a full-weight one-hot dimension here previously, which meant two
    # otherwise-identical messages could never land in the same micro-cluster if they differed
    # only by persona (a forced Euclidean gap of ~2*persona_weight regardless of how similar the
    # text/region were) -- that fragmented real campaigns into up to 6 near-duplicate clusters
    # (one per persona) and was the reason cluster counts looked implausibly high, so it was
    # removed. It's back now (product decision: alerts should be targeted by persona too, e.g. "a
    # student in Mumbai" vs "a senior citizen in Mumbai" both hit by the same credit-card-spam
    # campaign should form distinct, separately-alertable clusters), but at a deliberately LOW
    # weight relative to region/message_type -- persona_weight_multiplier is well below 1.0 so it
    # nudges/splits clusters only when region+message_type+text are already close, rather than
    # forcing a hard split on persona alone the way the previous full-weight attempt did. Verify
    # with `python -m ingestion.run_clustering` after any change to this weight: compare macro-
    # cluster count/size distribution against the last known-good run (see git history of
    # data/processed/blue_db_clusters.csv) -- an implausible jump in cluster count is the same
    # smoking gun that flagged the original regression.
    embedding_dim_raw: int = 384
    embedding_dim_reduced: int = 96   # 74 text-PCA + 2 region + 14 message_type + 6 persona (one-hot)
    region_weight_multiplier: float = 2.5
    message_type_weight_multiplier: float = 3.0
    persona_weight_multiplier: float = 0.35
    denstream_core_radius: float = 0.35
    denstream_outlier_promote_count: int = 5

    # Alerting -- messages in the trailing 24h a macro-cluster needs to trigger a threshold alert.
    # Kept low (10) deliberately for early rollout: with a young Blue DB corpus, few clusters will
    # ever reach a high count, and the cost of an unnecessary alert is far lower than the cost of a
    # real emerging campaign going unflagged. Raise this once real message volume justifies it.
    alert_threshold_message_count: int = 10


settings = Settings()
