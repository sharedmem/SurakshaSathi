"""Offline macro-clustering: runs on a schedule (async, separate from the live insertion path per
spec), groups dense micro-clusters into final readable "campaigns" via DBSCAN over micro-cluster
centroids. Because region is already baked into the centroid (region-weighted dims from
region_weighting.py), DBSCAN naturally keeps different regions in separate macro-clusters even
when the underlying fraud text is near-identical.
"""

from dataclasses import dataclass

import numpy as np
from sklearn.cluster import DBSCAN

from clustering.denstream import MicroCluster


@dataclass
class MacroCluster:
    macro_id: str
    micro_cluster_ids: list[str]
    total_weight: int
    representative_centroid: np.ndarray


def build_macro_clusters(
    micro_clusters: list[MicroCluster], eps: float = 0.6, min_samples: int = 1
) -> list[MacroCluster]:
    if not micro_clusters:
        return []

    centroids = np.stack([mc.centroid for mc in micro_clusters])
    # weight micro-clusters by their point mass so dense/heavy clusters dominate campaign shape
    sample_weight = np.array([mc.weight for mc in micro_clusters], dtype=float)

    labels = DBSCAN(eps=eps, min_samples=min_samples).fit_predict(
        centroids, sample_weight=sample_weight
    )

    macro_clusters: list[MacroCluster] = []
    for label in sorted(set(labels)):
        member_idxs = [i for i, l in enumerate(labels) if l == label]
        members = [micro_clusters[i] for i in member_idxs]
        total_weight = sum(m.weight for m in members)
        # weighted centroid = campaign's representative point
        rep = np.average(
            np.stack([m.centroid for m in members]),
            axis=0,
            weights=[m.weight for m in members],
        )
        macro_id = f"macro-{label}" if label != -1 else f"macro-singleton-{member_idxs[0]}"
        macro_clusters.append(
            MacroCluster(
                macro_id=macro_id,
                micro_cluster_ids=[m.cluster_id for m in members],
                total_weight=total_weight,
                representative_centroid=rep,
            )
        )
    return macro_clusters
