"""Runs the full clustering pipeline over the metadata-enriched Blue DB messages:
text -> PCA-reduced embedding -> + region/message_type/persona weighted dims -> DenStream online
micro-cluster routing (no fading, per spec) -> offline DBSCAN macro-clustering.

Clustering is driven primarily by region and message type, with persona as a low-weight
refinement and the text embedding as a secondary refinement within those buckets -- hence
region/message_type's weight multipliers in config/settings.py being > 1.0 relative to the
(normalized) text-PCA distance, while persona_weight_multiplier is deliberately kept well below
1.0 (see that setting's comment for the fragmentation regression a full-weight persona axis caused
previously, and why this weight must stay low). Persona is ALSO still recorded per cluster as a
majority vote over its members (for cases where a cluster ends up mixed) -- both mechanisms serve
alerting/targeting, but the feature-vector axis is what actually lets a single-persona cluster
form and be independently alerted when it's genuinely distinct from a mixed-persona one.

Input:  data/processed/blue_db_messages_enriched.csv (from enrich_blue_db_metadata.py)
Output: data/processed/blue_db_messages_clustered.csv  (messages + assigned cluster_id)
        data/processed/blue_db_clusters.csv             (micro + macro cluster records)
"""

import json
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

import numpy as np
import pandas as pd

from clustering.categorical_features import one_hot
from clustering.denstream import DenStreamRouter
from clustering.embedding import ReducedEmbedder
from clustering.macro_cluster import build_macro_clusters
from clustering.message_type import ALL_MESSAGE_TYPES
from clustering.persona import ALL_PERSONAS
from clustering.region_weighting import weighted_region_features
from config.settings import settings

RAG_MODEL_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = RAG_MODEL_ROOT / "data" / "processed"

REGION_DIMS = 2
MESSAGE_TYPE_DIMS = len(ALL_MESSAGE_TYPES)
PERSONA_DIMS = len(ALL_PERSONAS)
TEXT_DIMS = settings.embedding_dim_reduced - REGION_DIMS - MESSAGE_TYPE_DIMS - PERSONA_DIMS


def majority(values: list) -> str:
    values = [v for v in values if v and str(v) != "nan"]
    if not values:
        return "unknown"
    return Counter(values).most_common(1)[0][0]


def build_feature_vector(text_vector: np.ndarray, region: str, message_type: str, persona: str) -> np.ndarray:
    region_vec = weighted_region_features(region)
    message_type_vec = one_hot(message_type, ALL_MESSAGE_TYPES, settings.message_type_weight_multiplier)
    persona_vec = one_hot(persona, ALL_PERSONAS, settings.persona_weight_multiplier)
    return np.concatenate([text_vector, region_vec, message_type_vec, persona_vec])


def run():
    enriched_path = DATA_DIR / "blue_db_messages_enriched.csv"
    if not enriched_path.exists():
        raise FileNotFoundError(f"{enriched_path} missing -- run enrich_blue_db_metadata.py first")

    df = pd.read_csv(enriched_path)
    df["timestamp"] = pd.to_datetime(df["timestamp"])
    df = df.sort_values("timestamp").reset_index(drop=True)  # simulate streaming arrival order

    embedder = ReducedEmbedder(target_dim=TEXT_DIMS)
    embedder.fit(df["original_message"].tolist())
    text_vectors = embedder.embed(df["original_message"].tolist())

    router = DenStreamRouter()
    assigned_cluster_ids = []
    for i, row in df.iterrows():
        full_vector = build_feature_vector(text_vectors[i], row["region"], row["message_type"], row["persona"])
        cluster_id = router.insert(row["message_id"], full_vector)
        assigned_cluster_ids.append(cluster_id)

    df["cluster_id"] = assigned_cluster_ids

    micro_clusters = router.all_clusters()
    macro_clusters = build_macro_clusters(micro_clusters)

    # map micro_cluster_id -> macro_id
    micro_to_macro = {}
    for macro in macro_clusters:
        for micro_id in macro.micro_cluster_ids:
            micro_to_macro[micro_id] = macro.macro_id

    micro_by_id = {mc.cluster_id: mc for mc in micro_clusters}
    id_to_message = df.set_index("message_id")

    now = datetime.now(timezone.utc).isoformat()
    cluster_rows = []
    for mc in micro_clusters:
        member_rows = id_to_message.loc[mc.member_ids]
        region = majority(member_rows["region"].tolist())
        persona = majority(member_rows["persona"].tolist())
        fraud_type = majority(member_rows["message_type"].tolist())
        sample_message = member_rows.iloc[0]["original_message"]
        cluster_rows.append(
            {
                "cluster_id": mc.cluster_id,
                "cluster_type": "micro",
                "parent_macro_cluster_id": micro_to_macro.get(mc.cluster_id),
                "centroid_json": json.dumps(mc.centroid.tolist()),
                "weight": mc.weight,
                "radius": settings.denstream_core_radius,
                "sample_message": sample_message,
                "region": region,
                "persona": persona,
                "fraud_type": fraud_type,
                "created_at": now,
                "updated_at": now,
            }
        )

    for macro in macro_clusters:
        member_micro = [micro_by_id[mid] for mid in macro.micro_cluster_ids]
        heaviest = max(member_micro, key=lambda m: m.weight)
        all_member_ids = [mid for m in member_micro for mid in m.member_ids]
        member_rows = id_to_message.loc[all_member_ids]
        region = majority(member_rows["region"].tolist())
        persona = majority(member_rows["persona"].tolist())
        fraud_type = majority(member_rows["message_type"].tolist())
        cluster_rows.append(
            {
                "cluster_id": macro.macro_id,
                "cluster_type": "macro",
                "parent_macro_cluster_id": None,
                "centroid_json": json.dumps(macro.representative_centroid.tolist()),
                "weight": macro.total_weight,
                "radius": None,
                "sample_message": id_to_message.loc[heaviest.member_ids[0], "original_message"],
                "region": region,
                "persona": persona,
                "fraud_type": fraud_type,
                "created_at": now,
                "updated_at": now,
            }
        )

    clusters_df = pd.DataFrame(cluster_rows)
    DATA_DIR.mkdir(parents=True, exist_ok=True)
    df.to_csv(DATA_DIR / "blue_db_messages_clustered.csv", index=False)
    clusters_df.to_csv(DATA_DIR / "blue_db_clusters.csv", index=False)

    n_core = sum(1 for mc in micro_clusters if mc.is_core)
    print(f"Messages: {len(df)}")
    print(f"Micro-clusters: {len(micro_clusters)} ({n_core} core, {len(micro_clusters) - n_core} outlier)")
    print(f"Macro-clusters (campaigns): {len(macro_clusters)}")
    print()
    macro_summary = clusters_df[clusters_df["cluster_type"] == "macro"].sort_values(
        "weight", ascending=False
    )[["cluster_id", "weight", "region", "persona", "fraud_type", "sample_message"]].head(15)
    print("Top macro-clusters (campaigns) by message volume:")
    with pd.option_context("display.max_colwidth", 50):
        safe_output = macro_summary.to_string(index=False).encode("ascii", "replace").decode("ascii")
        print(safe_output)


if __name__ == "__main__":
    run()
