"""Scheduled job (weekly): crawls RBI's Financial Awareness Messages (FAME) page, chunks the
content the same way as ingest_sbi_documents.py, embeds it, and upserts into the pgvector
`guideline_docs` table. Low frequency because this content is near-static.
"""

from datetime import datetime, timezone
from uuid import uuid4

import httpx
from bs4 import BeautifulSoup
from langchain_text_splitters import RecursiveCharacterTextSplitter

from common.resilience import with_retry
from db.postgres_client import GuidelineDoc

FAME_URL = "https://www.rbi.org.in/financialeducation/fame.aspx"
CHUNK_SIZE = 512
CHUNK_OVERLAP = 50


@with_retry("rbi_fame", exceptions=(httpx.HTTPError,))
def fetch_fame_text() -> str:
    resp = httpx.get(FAME_URL, timeout=15, headers={"User-Agent": "SurakshaSathiBot/1.0 (+fraud-research)"})
    resp.raise_for_status()
    soup = BeautifulSoup(resp.text, "lxml")
    for tag in soup(["script", "style", "nav", "header", "footer"]):
        tag.decompose()
    return soup.get_text(separator="\n", strip=True)


def run(session, embedder) -> int:
    """`embedder` is ingestion.case_notes_pipeline.LocalMiniLmEmbedder (same 384-dim encoder used
    for SBIDocuments and case notes, so all three guideline/case-note sources are comparable)."""
    try:
        text = fetch_fame_text()
    except Exception as e:
        print(f"rbi_fame_sync_job: fetch failed, skipping this run -- {e}")
        return 0

    splitter = RecursiveCharacterTextSplitter(chunk_size=CHUNK_SIZE, chunk_overlap=CHUNK_OVERLAP)
    chunks = splitter.split_text(text)
    if not chunks:
        return 0

    embeddings = embedder.embed(chunks)
    now = datetime.now(timezone.utc)
    for chunk_text, embedding in zip(chunks, embeddings):
        session.merge(
            GuidelineDoc(
                doc_id=f"rbi-fame-{uuid4().hex[:8]}",
                source_file="rbi_fame_page",
                chunk_text=chunk_text,
                embedding=embedding,
                ingested_at=now,
            )
        )
    session.commit()
    return len(chunks)
