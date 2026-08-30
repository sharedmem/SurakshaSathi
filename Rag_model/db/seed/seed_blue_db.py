"""Loads the clustered, metadata-enriched output of ingestion/run_clustering.py into Cloud SQL.
Requires POSTGRES_DSN to be set (see .env.example) -- until then, run ingestion/run_clustering.py
on its own; its output files are the "what would go into Blue DB" artifact.

Uses bulk INSERT ... ON CONFLICT batches, not a per-row ORM merge() loop -- merge() does a
round-trip SELECT-then-INSERT/UPDATE per row, which over a network connection to a Cloud SQL
public IP (rather than localhost) turns thousands of rows into thousands of round-trips. The
first run of this script that way effectively hung; batched bulk upserts finish in seconds.
"""

import hashlib
import json
from datetime import datetime, timezone
from pathlib import Path

import pandas as pd
from sqlalchemy.dialects.postgresql import insert as pg_insert

from config.settings import settings
from db.postgres_client import Cluster, Message, UserMap, get_engine, get_session_factory, init_db

DATA_DIR = Path(__file__).resolve().parent.parent.parent / "data" / "processed"
BATCH_SIZE = 500


def _bulk_upsert(session, model, rows: list[dict], conflict_col: str) -> None:
    for i in range(0, len(rows), BATCH_SIZE):
        batch = rows[i : i + BATCH_SIZE]
        stmt = pg_insert(model).values(batch)
        update_cols = {c: stmt.excluded[c] for c in batch[0] if c != conflict_col}
        stmt = stmt.on_conflict_do_update(index_elements=[conflict_col], set_=update_cols)
        session.execute(stmt)
        session.commit()
        print(f"  {model.__tablename__}: {min(i + BATCH_SIZE, len(rows))}/{len(rows)}", flush=True)


def _user_id_for_phone(phone: str) -> str:
    """Deterministic, stable user_id derived from phone -- reruns of this script upsert the same
    synthetic user rather than minting duplicates each time."""
    return "synth-" + hashlib.sha256(phone.encode("utf-8")).hexdigest()[:16]


def seed_users(messages_df: pd.DataFrame) -> list[dict]:
    """Derives UserMap rows from blue_db_messages_clustered.csv's user_name/user_phone/region/
    persona columns -- there is no separate synthetic-user dataset, and (per the real product
    flow) UserMap is exactly this: one row per app install, keyed by identity, carrying the
    region/persona that gets denormalized onto every message that install reports.

    One row per unique user_phone (first occurrence wins if the same synthetic phone appears
    against multiple region/persona combinations, which can happen since
    enrich_blue_db_metadata.py samples region/persona per-message, not per-user -- acceptable for
    demo data; a real UserMap is set once at install and doesn't drift per-message).

    fcm_token is a clearly-synthetic placeholder (`demo-fcm-<user_id>`), not a real device token --
    alerting/daily_job.py::users_in_region reads it from metadata_json, so without this column the
    daily job would find zero recipients even after a cluster crosses its threshold. Wiring a real
    push token requires an actual app install to register one; this keeps the demo pipeline
    self-consistent until that exists.
    """
    if "user_phone" not in messages_df.columns:
        return []

    with_phone = messages_df[messages_df["user_phone"].notna() & (messages_df["user_phone"] != "")]
    deduped = with_phone.drop_duplicates(subset="user_phone", keep="first")

    rows = []
    for _, row in deduped.iterrows():
        user_id = _user_id_for_phone(row["user_phone"])
        rows.append(
            {
                "user_id": user_id,
                "display_name": row.get("user_name") or None,
                "phone_number": row["user_phone"],
                "region": row.get("region") or None,
                "persona": row.get("persona") or None,
                "lat": None,
                "lon": None,
                "is_vulnerable_group": row.get("persona") in ("senior_citizen", "homemaker"),
                "metadata_json": {"fcm_token": f"demo-fcm-{user_id}", "source": "synthetic_demo_seed"},
            }
        )
    return rows


def seed():
    if not settings.postgres_dsn:
        raise RuntimeError(
            "POSTGRES_DSN not set. Run ingestion/run_clustering.py first to produce the "
            "local artifact, then supply POSTGRES_DSN and re-run this script to load it."
        )

    engine = get_engine()
    init_db(engine)
    Session = get_session_factory()

    messages_df = pd.read_csv(DATA_DIR / "blue_db_messages_clustered.csv", dtype={"user_phone": str, "sender": str})
    clusters_df = pd.read_csv(DATA_DIR / "blue_db_clusters.csv")
    now = datetime.now(timezone.utc)

    cluster_rows = [
        {
            "cluster_id": row["cluster_id"],
            "cluster_type": row["cluster_type"],
            "parent_macro_cluster_id": row.get("parent_macro_cluster_id") or None,
            "centroid": json.loads(row["centroid_json"]),
            "weight": int(row["weight"]),
            "radius": float(row["radius"]) if pd.notna(row.get("radius")) else None,
            "sample_message": row["sample_message"],
            "region": row.get("region"),
            "persona": row.get("persona"),
            "fraud_type": row.get("fraud_type"),
            "retrieved_docs": [],
            "docs_retrieved_at": None,
            "created_at": now,
            "updated_at": now,
        }
        for _, row in clusters_df.iterrows()
    ]

    message_rows = [
        {
            "message_id": row["message_id"],
            "original_message": row["original_message"],
            "language": row["language"],
            "message_english": row.get("message_english") or None,
            "needs_translation": bool(row["needs_translation"]),
            "channel": row["channel"],
            "content_type": row["content_type"],
            "region": row.get("region"),
            "persona": row.get("persona"),
            "message_type": row.get("message_type"),
            "sender": row.get("sender"),
            "user_name": row.get("user_name") or None,
            "user_phone": row.get("user_phone") or None,
            "cluster_id": row.get("cluster_id") or None,
            "timestamp": pd.to_datetime(row["timestamp"]),
        }
        for _, row in messages_df.iterrows()
    ]

    user_rows = seed_users(messages_df)

    with Session() as session:
        print(f"Upserting {len(cluster_rows)} clusters...", flush=True)
        _bulk_upsert(session, Cluster, cluster_rows, "cluster_id")
        print(f"Upserting {len(message_rows)} messages...", flush=True)
        _bulk_upsert(session, Message, message_rows, "message_id")
        if user_rows:
            print(f"Upserting {len(user_rows)} synthetic users...", flush=True)
            _bulk_upsert(session, UserMap, user_rows, "user_id")

    print(
        f"Seeded {len(clusters_df)} clusters, {len(messages_df)} messages, "
        f"and {len(user_rows)} synthetic users into Cloud SQL.",
        flush=True,
    )


if __name__ == "__main__":
    seed()
