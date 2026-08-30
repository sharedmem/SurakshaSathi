"""Coordinate-based regional weighting (per spec): map each region to lat/lon, scale to 0-1,
then multiply by REGION_WEIGHT_MULTIPLIER before concatenating onto the message embedding. This
artificially stretches inter-region distance so DenStream naturally separates e.g. a
Maharashtra-electricity-scam micro-cluster from a Delhi-electricity-scam one, instead of merging
them just because the message text is near-identical.

REGIONS covers all 28 Indian states + 8 union territories (36 total) -- the Feature Map's "All
Regions" picker, heatmap, and campaign view must be able to show every state, not just a handful
of metros. Coordinates are each state/UT's capital (or geographic centroid for UTs without one).
Sampling weight is a rough population/fraud-report-volume proxy, skewed toward the highest-volume
states (Maharashtra, Uttar Pradesh, Delhi, Karnataka, Tamil Nadu, West Bengal) per spec; every
other state still gets realistic non-zero representation rather than being omitted.
"""

from config.settings import settings

# (lat, lon, sampling_weight) - weight is relative probability mass for synthetic/seed assignment,
# NOT used at inference time (real messages carry their own region from user_map / app install location).
REGIONS: dict[str, tuple[float, float, float]] = {
    # -- States, roughly ordered by sampling weight --
    "Maharashtra": (19.0760, 72.8777, 0.115),
    "Uttar Pradesh": (26.8467, 80.9462, 0.100),
    "Karnataka": (12.9716, 77.5946, 0.070),
    "Tamil Nadu": (13.0827, 80.2707, 0.060),
    "West Bengal": (22.5726, 88.3639, 0.060),
    "Gujarat": (23.2156, 72.6369, 0.055),
    "Telangana": (17.3850, 78.4867, 0.050),
    "Andhra Pradesh": (16.5062, 80.6480, 0.045),
    "Rajasthan": (26.9124, 75.7873, 0.040),
    "Madhya Pradesh": (23.2599, 77.4126, 0.035),
    "Bihar": (25.5941, 85.1376, 0.035),
    "Haryana": (30.7333, 76.7794, 0.030),
    "Kerala": (8.5241, 76.9366, 0.025),
    "Punjab": (30.7333, 76.7794, 0.025),
    "Odisha": (20.2961, 85.8245, 0.020),
    "Jharkhand": (23.3441, 85.3096, 0.018),
    "Assam": (26.1445, 91.7362, 0.016),
    "Chhattisgarh": (21.2514, 81.6296, 0.014),
    "Uttarakhand": (30.3165, 78.0322, 0.010),
    "Himachal Pradesh": (31.1048, 77.1734, 0.007),
    "Goa": (15.4909, 73.8278, 0.005),
    "Tripura": (23.8315, 91.2868, 0.003),
    "Manipur": (24.8170, 93.9368, 0.003),
    "Meghalaya": (25.5788, 91.8933, 0.003),
    "Nagaland": (25.6751, 94.1086, 0.002),
    "Mizoram": (23.7271, 92.7176, 0.002),
    "Sikkim": (27.3389, 88.6065, 0.002),
    "Arunachal Pradesh": (27.0844, 93.6053, 0.002),
    # -- Union territories --
    "Delhi": (28.7041, 77.1025, 0.085),
    "Jammu and Kashmir": (34.0837, 74.7973, 0.008),
    "Puducherry": (11.9416, 79.8083, 0.004),
    "Chandigarh": (30.7333, 76.7794, 0.004),
    "Andaman and Nicobar Islands": (11.6234, 92.7265, 0.0015),
    "Ladakh": (34.1526, 77.5771, 0.001),
    "Dadra and Nagar Haveli and Daman and Diu": (20.3974, 72.8328, 0.001),
    "Lakshadweep": (10.5593, 72.6358, 0.0005),
    "Unknown": (22.3511, 78.6677, 0.001),  # geographic center of India, low-weight fallback
}

# Highest fraud-report-volume states -- used to round out the Feature Map's region ordering
# (own state -> nearby states -> popular states -> everything else) when a user's location
# doesn't happen to be near a high-traffic state.
POPULAR_REGIONS: list[str] = [
    "Maharashtra", "Uttar Pradesh", "Delhi", "Karnataka", "Tamil Nadu", "West Bengal",
    "Gujarat", "Telangana",
]

_LAT_MIN, _LAT_MAX = 6.5, 35.5   # India's approximate lat bounding box
_LON_MIN, _LON_MAX = 68.0, 97.5  # India's approximate lon bounding box


def region_names_and_weights() -> tuple[list[str], list[float]]:
    names = list(REGIONS.keys())
    weights = [REGIONS[n][2] for n in names]
    return names, weights


def region_to_scaled_vector(region: str) -> list[float]:
    """Returns the [lat_scaled, lon_scaled] pair for a region, each in [0, 1], pre-weight-multiply."""
    lat, lon, _ = REGIONS.get(region, REGIONS["Unknown"])
    lat_scaled = (lat - _LAT_MIN) / (_LAT_MAX - _LAT_MIN)
    lon_scaled = (lon - _LON_MIN) / (_LON_MAX - _LON_MIN)
    return [lat_scaled, lon_scaled]


def weighted_region_features(region: str) -> list[float]:
    """The two extra dimensions appended to a message's PCA-reduced embedding before DenStream."""
    lat_scaled, lon_scaled = region_to_scaled_vector(region)
    w = settings.region_weight_multiplier
    return [lat_scaled * w, lon_scaled * w]


def _haversine_km(lat1: float, lon1: float, lat2: float, lon2: float) -> float:
    from math import asin, cos, radians, sin, sqrt

    r_earth_km = 6371.0
    dlat, dlon = radians(lat2 - lat1), radians(lon2 - lon1)
    a = sin(dlat / 2) ** 2 + cos(radians(lat1)) * cos(radians(lat2)) * sin(dlon / 2) ** 2
    return 2 * r_earth_km * asin(sqrt(a))


def nearest_regions(region: str, k: int = 3) -> list[str]:
    """The k nearest other regions to `region`, by great-circle distance -- powers "nearby region"
    alerts (a user in one state should also see high-signal alerts from neighboring states)."""
    if region not in REGIONS:
        return []
    lat, lon, _ = REGIONS[region]
    distances = sorted(
        (
            (_haversine_km(lat, lon, other_lat, other_lon), name)
            for name, (other_lat, other_lon, _) in REGIONS.items()
            if name != region
        ),
    )
    return [name for _, name in distances[:k]]
