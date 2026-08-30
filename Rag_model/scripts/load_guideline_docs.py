"""Stage 2 of the guideline ingestion pipeline (see ingestion/ingest_sbi_documents.py's module
docstring): loads the pre-embedded chunks from data/processed/guideline_chunks_staged.jsonl into
the pgvector-backed guideline_docs table. Requires POSTGRES_DSN to be set.
"""

import json
from pathlib import Path

from db.postgres_client import get_engine, get_session_factory, init_db
from db.vector_store import upsert_guideline_chunks

STAGING_PATH = Path(__file__).resolve().parent.parent / "data" / "processed" / "guideline_chunks_staged.jsonl"


def main():
    engine = get_engine()
    init_db(engine)

    chunks = []
    with open(STAGING_PATH, encoding="utf-8") as f:
        for line in f:
            row = json.loads(line)
            if row.get("embedding") is None:
                continue
            chunks.append(row)

    print(f"Loaded {len(chunks)} embedded chunks from {STAGING_PATH}")

    Session = get_session_factory()
    with Session() as session:
        upsert_guideline_chunks(session, chunks)

    print(f"Upserted {len(chunks)} guideline_docs rows into Postgres.")


if __name__ == "__main__":
    main()
