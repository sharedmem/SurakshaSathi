"""Single Cloud SQL for PostgreSQL instance backing both Blue DB (messages/clusters/user_map/
national_digest/threat_intel_urls) and the guidelines vector store (pgvector). Consolidating
onto one GCP-native instance instead of MongoDB Atlas + Pinecone avoids a cross-cloud network hop
on both the hot per-message path and the batch alerting path, and is cheaper to run at this scale.

Local/dev: connects via POSTGRES_DSN directly.
Cloud Run: prefer the Cloud SQL Python Connector (IAM auth, no public IP, automatic mTLS) --
swap `get_engine()`'s local branch for the connector creator shown commented below.
"""

from sqlalchemy import (
    Column, DateTime, Float, ForeignKey, Integer, String, Boolean, JSON, create_engine, text
)
from sqlalchemy.orm import declarative_base, sessionmaker
from pgvector.sqlalchemy import Vector

from config.settings import settings

Base = declarative_base()


class Message(Base):
    __tablename__ = "messages"
    message_id = Column(String, primary_key=True)
    original_message = Column(String, nullable=False)
    language = Column(String, nullable=False)
    message_english = Column(String, nullable=True)
    needs_translation = Column(Boolean, default=True)
    channel = Column(String, nullable=False)
    content_type = Column(String, nullable=False)
    region = Column(String, nullable=True)
    # persona/message_type denormalized from the reporting user's UserMap.persona at ingestion
    # time. message_type + region + persona together are the clustering signal (see
    # clustering/run_clustering.py::build_feature_vector).
    persona = Column(String, nullable=True)
    message_type = Column(String, nullable=True)
    sender = Column(String, nullable=True)  # the fraud message's spoofed originator, NOT the recipient
    user_name = Column(String, nullable=True)   # recipient identity -- see common/schemas.py:MessageRecord
    user_phone = Column(String, nullable=True)  # recipient identity -- see common/schemas.py:MessageRecord
    cluster_id = Column(String, ForeignKey("clusters.cluster_id"), nullable=True)
    timestamp = Column(DateTime(timezone=True), nullable=False)


class Cluster(Base):
    __tablename__ = "clusters"
    cluster_id = Column(String, primary_key=True)
    cluster_type = Column(String, nullable=False)  # "micro" | "macro"
    parent_macro_cluster_id = Column(String, nullable=True)
    centroid = Column(Vector(settings.pgvector_dim), nullable=False)
    weight = Column(Integer, default=1)
    radius = Column(Float, nullable=True)  # macro clusters have no fixed radius (DBSCAN eps, not per-cluster)
    sample_message = Column(String, nullable=False)
    region = Column(String, nullable=True)
    persona = Column(String, nullable=True)   # majority persona among member messages
    fraud_type = Column(String, nullable=True)  # majority message_type among member messages
    retrieved_docs = Column(JSON, default=list)
    docs_retrieved_at = Column(DateTime(timezone=True), nullable=True)
    created_at = Column(DateTime(timezone=True), nullable=False)
    updated_at = Column(DateTime(timezone=True), nullable=False)


class UserMap(Base):
    """Set once at app install (region + persona), per the actual product flow -- these two
    fields are what's denormalized onto Message.region/Message.persona at ingestion time."""
    __tablename__ = "user_map"
    user_id = Column(String, primary_key=True)
    display_name = Column(String, nullable=True)
    phone_number = Column(String, nullable=True, index=True)  # indexed: dedup key for db/seed/seed_blue_db.py::seed_users
    region = Column(String, nullable=True)
    persona = Column(String, nullable=True)  # see clustering/persona.py for the fixed category set
    email = Column(String, nullable=True)  # collected on the Android app's Registration screen
    preferred_language = Column(String, nullable=True)  # BCP-47-ish tag: en, hi, mr, ta, ...
    lat = Column(Float, nullable=True)
    lon = Column(Float, nullable=True)
    is_vulnerable_group = Column(Boolean, default=False)
    metadata_json = Column(JSON, default=dict)


class NationalDigest(Base):
    __tablename__ = "national_digest"
    id = Column(Integer, primary_key=True, autoincrement=True)
    date = Column(DateTime(timezone=True), nullable=False)
    region = Column(String, nullable=False)
    fraud_type = Column(String, nullable=True)
    report_count = Column(Integer, nullable=False)
    source = Column(String, default="cybercrime.gov.in")


class ThreatIntelUrl(Base):
    __tablename__ = "threat_intel_urls"
    url_hash = Column(String, primary_key=True)
    url = Column(String, nullable=False)
    first_seen = Column(DateTime(timezone=True), nullable=False)
    source = Column(String, default="openphish")


class GuidelineDoc(Base):
    """The pgvector-backed replacement for the Pinecone `guidelines` namespace. Uses 384 dims
    (all-MiniLM-L6-v2, same encoder as case_note_docs) -- NOT settings.pgvector_dim (64), which is
    the message-clustering PCA-reduced space; these are two unrelated embedding spaces."""
    __tablename__ = "guideline_docs"
    doc_id = Column(String, primary_key=True)
    source_file = Column(String, nullable=False)
    chunk_text = Column(String, nullable=False)
    embedding = Column(Vector(384), nullable=False)
    ingested_at = Column(DateTime(timezone=True), nullable=False)


class CaseNoteDoc(Base):
    """PII-REDACTED chunks from NCRP complaint logs / user telemetry / SBI case notes. Kept in a
    separate table from GuidelineDoc: different retention policy, different access control -- this
    is sensitive operational data (even after redaction), not a public reference corpus. Uses its
    own vector width (384, matching all-MiniLM-L6-v2) rather than pgvector_dim (64, the message-
    clustering dimension) since these two embedding spaces are unrelated."""
    __tablename__ = "case_note_docs"
    doc_id = Column(String, primary_key=True)
    source = Column(String, nullable=False)          # "ncrp" | "user_telemetry" | "sbi_case_notes"
    chunk_text = Column(String, nullable=False)       # already PII-redacted before this is written
    embedding = Column(Vector(384), nullable=False)
    ingested_at = Column(DateTime(timezone=True), nullable=False)


def get_engine():
    if not settings.postgres_dsn:
        raise RuntimeError("POSTGRES_DSN not set -- see .env.example")
    # requirements.txt installs psycopg (v3), not psycopg2 -- SQLAlchemy defaults a bare
    # "postgresql://" DSN to the psycopg2 driver, so normalize the scheme to force psycopg3.
    dsn = settings.postgres_dsn
    if dsn.startswith("postgresql://"):
        dsn = dsn.replace("postgresql://", "postgresql+psycopg://", 1)
    # Cloud Run alternative (no public IP, IAM-authenticated):
    #   from google.cloud.sql.connector import Connector
    #   connector = Connector()
    #   def getconn():
    #       return connector.connect(INSTANCE_CONNECTION_NAME, "psycopg", ...)
    #   return create_engine("postgresql+psycopg://", creator=getconn)
    #
    # connect_timeout is deliberately short (default psycopg/OS TCP timeout can be 60s+, which
    # blew through the Android client's 30s read timeout on message_scan.py's request path --
    # every request silently fell back to canned offline text instead of the real LLM-generated
    # warning, because retrieve_guideline_docs() never got a chance to fail fast and let the
    # request continue without grounding docs). A real connectivity problem (e.g. Cloud SQL
    # authorized-networks not including this host) should surface in ~3s, not hang the request.
    return create_engine(dsn, connect_args={"connect_timeout": 3})


def get_session_factory():
    engine = get_engine()
    return sessionmaker(bind=engine)


def init_db(engine) -> None:
    with engine.connect() as conn:
        conn.execute(text("CREATE EXTENSION IF NOT EXISTS vector"))
        conn.commit()
    Base.metadata.create_all(engine)
