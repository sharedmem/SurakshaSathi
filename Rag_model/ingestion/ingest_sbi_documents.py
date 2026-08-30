"""Parses Rag_model/SBIDocuments/*.pdf (RBI/TRAI advisories, malware research, phishing-example
docs) into chunks and prepares them for the pgvector `guideline_docs` table. This is the static
fraud-education reference corpus the LLM grounds its explanations against (Output 1 + Output 2).

Two-stage by design: (1) extract+chunk+embed and write a local staging file, runnable right now
with no credentials; (2) `db/vector_store.upsert_guideline_chunks` loads the staging file into
Cloud SQL once POSTGRES_DSN is available. Keeping these separate means re-running PDF parsing
doesn't require a live DB connection, and vice versa.
"""

import json
from datetime import datetime, timezone
from pathlib import Path
from uuid import uuid4

from langchain_text_splitters import RecursiveCharacterTextSplitter
from pypdf import PdfReader

RAG_MODEL_ROOT = Path(__file__).resolve().parent.parent
SBI_DOCS_DIR = RAG_MODEL_ROOT / "SBIDocuments"
STAGING_PATH = RAG_MODEL_ROOT / "data" / "processed" / "guideline_chunks_staged.jsonl"

CHUNK_SIZE = 512
CHUNK_OVERLAP = 50

# Raw TRAI DLT header/prefix registries, not fraud-education guidance -- List_SMS_Headers alone
# is 1930 of 2678 total chunks (72%) and its sheer volume drowned out genuinely relevant malware/
# phishing docs in nearest-neighbor search (verified live: an APK-permission query returned
# company-name listings instead of the actual Android malware docs). These lookup tables belong
# in url_qr_callback_engine/callback_pipeline.py as a real DLT registry check, not in the RAG
# retrieval corpus.
EXCLUDED_FILENAMES = {"List_SMS_Headers_16062020_0.pdf", "Detail_Header_Prefixes_16062020_0.pdf"}


def extract_pdf_text(pdf_path: Path) -> str:
    reader = PdfReader(str(pdf_path))
    return "\n".join(page.extract_text() or "" for page in reader.pages)


def chunk_and_stage() -> list[dict]:
    splitter = RecursiveCharacterTextSplitter(chunk_size=CHUNK_SIZE, chunk_overlap=CHUNK_OVERLAP)
    now = datetime.now(timezone.utc).isoformat()
    staged = []

    pdf_paths = sorted(SBI_DOCS_DIR.glob("*.pdf"))
    for pdf_path in pdf_paths:
        if pdf_path.name in EXCLUDED_FILENAMES:
            print(f"SKIP (lookup table, not RAG content -- see EXCLUDED_FILENAMES): {pdf_path.name}")
            continue
        try:
            text = extract_pdf_text(pdf_path)
        except Exception as e:
            print(f"SKIP (unreadable): {pdf_path.name} -- {e}")
            continue
        if not text.strip():
            print(f"SKIP (no extractable text, likely scanned image): {pdf_path.name}")
            continue

        chunks = splitter.split_text(text)
        for chunk_text in chunks:
            staged.append(
                {
                    "doc_id": f"{pdf_path.stem}-{uuid4().hex[:8]}",
                    "source_file": pdf_path.name,
                    "chunk_text": chunk_text,
                    "embedding": None,  # filled in by embed_staged_chunks() -- production embedder
                    "ingested_at": now,
                }
            )
        print(f"{pdf_path.name}: {len(chunks)} chunks")

    return staged


def embed_staged_chunks(staged: list[dict]) -> list[dict]:
    """Production embedding: same 384-dim all-MiniLM-L6-v2 encoder used for case notes, so the
    guidelines corpus and case-notes corpus are comparable if ever queried together. NOT run
    automatically here -- this machine's C: drive has 0 bytes free, blocking the torch install
    (see docs/ENVIRONMENT_NOTE.md). Call this once sentence-transformers is available (Cloud Run,
    or after freeing local disk space)."""
    from ingestion.case_notes_pipeline import LocalMiniLmEmbedder

    embedder = LocalMiniLmEmbedder()
    texts = [c["chunk_text"] for c in staged]
    embeddings = embedder.embed(texts)
    for c, emb in zip(staged, embeddings):
        c["embedding"] = emb
    return staged


def main():
    staged = chunk_and_stage()
    STAGING_PATH.parent.mkdir(parents=True, exist_ok=True)
    with open(STAGING_PATH, "w", encoding="utf-8") as f:
        for row in staged:
            f.write(json.dumps(row, ensure_ascii=False) + "\n")
    print(f"\nStaged {len(staged)} chunks from {len(list(SBI_DOCS_DIR.glob('*.pdf')))} PDFs to {STAGING_PATH}")
    print("Embeddings NOT computed yet (needs sentence-transformers -- blocked by local disk space).")
    print("Run embed_staged_chunks() once available, then db.vector_store.upsert_guideline_chunks()")
    print("once POSTGRES_DSN is set, to finish loading into Cloud SQL.")


if __name__ == "__main__":
    main()
