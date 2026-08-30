"""Guidelines vector store operations (pgvector). Replaces the Pinecone `guidelines` namespace
from the original design -- same role (semantic retrieval of fraud-education reference docs for
the LLM prompt), backed by the same Cloud SQL instance as Blue DB.
"""

from datetime import datetime, timezone

from sqlalchemy.dialects.postgresql import insert as pg_insert
from sqlalchemy.orm import Session

from config.settings import settings
from db.postgres_client import GuidelineDoc

_guideline_embedder = None  # lazy singleton -- loading the sentence-transformer per call would be slow


def _get_guideline_embedder():
    global _guideline_embedder
    if _guideline_embedder is None:
        from ingestion.case_notes_pipeline import LocalMiniLmEmbedder

        _guideline_embedder = LocalMiniLmEmbedder()
    return _guideline_embedder


def upsert_guideline_chunks(session: Session, chunks: list[dict], batch_size: int = 500) -> None:
    """chunks: [{"doc_id": ..., "source_file": ..., "chunk_text": ..., "embedding": [...] }, ...].
    One bulk INSERT ... ON CONFLICT per batch instead of a per-row ORM merge -- merge() does a
    round-trip SELECT-then-INSERT/UPDATE per row, which over a network connection to Cloud SQL
    (rather than localhost) turned thousands of rows into thousands of round-trips and was the
    actual bottleneck the first time this was run."""
    now = datetime.now(timezone.utc)
    rows = [
        {
            "doc_id": c["doc_id"],
            "source_file": c["source_file"],
            "chunk_text": c["chunk_text"],
            "embedding": c["embedding"],
            "ingested_at": now,
        }
        for c in chunks
    ]

    for i in range(0, len(rows), batch_size):
        batch = rows[i : i + batch_size]
        stmt = pg_insert(GuidelineDoc).values(batch)
        stmt = stmt.on_conflict_do_update(
            index_elements=["doc_id"],
            set_={
                "chunk_text": stmt.excluded.chunk_text,
                "embedding": stmt.excluded.embedding,
                "ingested_at": stmt.excluded.ingested_at,
            },
        )
        session.execute(stmt)
        session.commit()


def semantic_search(session: Session, query_embedding: list[float], top_k: int = 5) -> list[GuidelineDoc]:
    """Nearest-neighbour search via pgvector's cosine-distance operator (<=>)."""
    return (
        session.query(GuidelineDoc)
        .order_by(GuidelineDoc.embedding.cosine_distance(query_embedding))
        .limit(top_k)
        .all()
    )


def retrieve_guideline_docs(text: str, top_k: int = 3) -> list[str]:
    """Embeds [text] and returns the top_k nearest fraud-education reference chunks as plain
    strings, ready to drop into an LLM prompt. Shared by both RAG-generation call sites:
    api/routes/message_scan.py (per-message scan) and alerting/daily_job.py (threshold-triggered
    regional alerts, using a cluster's representative sample_message as [text]) -- both need the
    same "generate a real ThreatReport grounded in retrieved guidance" behavior, not just a raw
    LLM call with no retrieval.

    Degrades gracefully (empty list) if Cloud SQL isn't provisioned or the embed/query fails --
    the caller's LLM prompt already handles an empty retrieved_docs list.
    """
    if not settings.postgres_dsn:
        return []
    try:
        from db.postgres_client import get_session_factory

        # guideline_docs uses the same 384-dim all-MiniLM-L6-v2 space as ingest_sbi_documents.py --
        # NOT clustering.embedding's PCA-reduced 96-dim space, which is for message clustering only.
        query_embedding = _get_guideline_embedder().embed([text])[0]

        Session = get_session_factory()
        with Session() as session:
            docs = semantic_search(session, query_embedding=query_embedding, top_k=top_k)
            return [d.chunk_text for d in docs]
    except Exception:
        return []
