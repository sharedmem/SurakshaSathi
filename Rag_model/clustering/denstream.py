"""Online micro-cluster router (DenStream-style), deliberately WITHOUT the fading/decay step —
per spec, clusters never shrink or get deleted for going quiet, they just stop growing. Each new
message vector (PCA-reduced embedding + region-weighted lat/lon dims) is routed in O(active
micro-clusters) time: no re-scan of historical messages.

Routing rule per message:
  1. Within radius of an existing CORE micro-cluster?  -> absorb, update centroid.
  2. Else within radius of an existing OUTLIER micro-cluster? -> absorb; promote to CORE once its
     point count crosses `outlier_promote_count`.
  3. Else -> spin up a new OUTLIER micro-cluster for this single message.
"""

import uuid
from dataclasses import dataclass, field

import numpy as np

from config.settings import settings


@dataclass
class MicroCluster:
    cluster_id: str
    centroid: np.ndarray
    weight: int = 1
    is_core: bool = False
    member_ids: list[str] = field(default_factory=list)

    def radius(self) -> float:
        # Fixed radius model (no variance tracking needed at seed-data scale); kept as a method
        # so a future upgrade to variance-based radius doesn't change call sites.
        return settings.denstream_core_radius

    def absorb(self, vector: np.ndarray, message_id: str) -> None:
        # Running mean update: centroid moves toward the new point, weighted by prior mass.
        self.centroid = (self.centroid * self.weight + vector) / (self.weight + 1)
        self.weight += 1
        self.member_ids.append(message_id)


class DenStreamRouter:
    def __init__(self):
        self.core_clusters: list[MicroCluster] = []
        self.outlier_clusters: list[MicroCluster] = []

    def _nearest(self, clusters: list[MicroCluster], vector: np.ndarray) -> tuple[MicroCluster | None, float]:
        if not clusters:
            return None, float("inf")
        dists = [np.linalg.norm(c.centroid - vector) for c in clusters]
        idx = int(np.argmin(dists))
        return clusters[idx], dists[idx]

    def insert(self, message_id: str, vector: np.ndarray) -> str:
        """Routes one message vector. Returns the micro-cluster id it landed in."""
        core, core_dist = self._nearest(self.core_clusters, vector)
        if core is not None and core_dist <= core.radius():
            core.absorb(vector, message_id)
            return core.cluster_id

        outlier, outlier_dist = self._nearest(self.outlier_clusters, vector)
        if outlier is not None and outlier_dist <= outlier.radius():
            outlier.absorb(vector, message_id)
            if outlier.weight >= settings.denstream_outlier_promote_count:
                outlier.is_core = True
                self.outlier_clusters.remove(outlier)
                self.core_clusters.append(outlier)
            return outlier.cluster_id

        new_cluster = MicroCluster(
            cluster_id=str(uuid.uuid4()), centroid=vector.copy(), member_ids=[message_id]
        )
        self.outlier_clusters.append(new_cluster)
        return new_cluster.cluster_id

    def all_clusters(self) -> list[MicroCluster]:
        return self.core_clusters + self.outlier_clusters
