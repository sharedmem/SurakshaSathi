"""Shared one-hot + weight-multiply helper for categorical clustering dimensions (persona,
message_type) -- same pattern as region_weighting.py's lat/lon scaling, generalized to arbitrary
categories instead of geographic coordinates.
"""


def one_hot(category: str, all_categories: list[str], weight: float) -> list[float]:
    """Returns a len(all_categories)-length vector: `weight` at the matching index, 0 elsewhere.
    An unrecognized category maps to the all-zeros vector (deliberately -- it should sit at the
    origin of this sub-space rather than be forced into an arbitrary bucket)."""
    vec = [0.0] * len(all_categories)
    if category in all_categories:
        vec[all_categories.index(category)] = weight
    return vec
