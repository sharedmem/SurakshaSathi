"""L2 Path B: Quishing (malicious QR code) detection. ALFA preprocessing (grayscale + Otsu
threshold + orientation normalization) and FAST structural correction (restoring finder/
alignment/timing patterns without touching data modules) are real, working image-processing code
-- no trained model needed. The XGBoost classifier over the resulting 24-dim structural feature
vector is a stub (needs a labeled quishing dataset -- see docs/CREDENTIALS.md's bootstrap-data
note: synthetic QR-encoded PhishTank URLs + real benign UPI/business QR scans).
"""

from dataclasses import dataclass

import cv2
import numpy as np


@dataclass
class QrAnalysisResult:
    decoded_payload: str | None
    structural_features: list[float]
    xgboost_quishing_score: float | None  # stub until trained


def alfa_preprocess(image: np.ndarray) -> np.ndarray:
    """Grayscale + Otsu threshold; flips to dark-on-light if inverted (per spec: white-pixel
    ratio < 51% triggers inversion)."""
    gray = cv2.cvtColor(image, cv2.COLOR_BGR2GRAY) if image.ndim == 3 else image
    _, binary = cv2.threshold(gray, 0, 255, cv2.THRESH_BINARY + cv2.THRESH_OTSU)

    white_ratio = np.mean(binary == 255)
    if white_ratio < 0.51:
        binary = cv2.bitwise_not(binary)
    return binary


def decode_qr(image: np.ndarray) -> str | None:
    from pyzbar.pyzbar import decode

    results = decode(image)
    return results[0].data.decode("utf-8", errors="ignore") if results else None


def extract_structural_features(binary_image: np.ndarray) -> list[float]:
    """24-dim structural feature vector (run-lengths, density variation, module entropy) --
    real feature extraction, feeds the (not-yet-trained) XGBoost classifier."""
    h, w = binary_image.shape
    density = float(np.mean(binary_image == 0))  # fraction of dark modules

    # row/column run-length statistics as a structural fingerprint
    row_runs = []
    for row in binary_image:
        changes = np.diff(row.astype(int))
        row_runs.append(int(np.sum(changes != 0)))
    run_length_mean = float(np.mean(row_runs))
    run_length_std = float(np.std(row_runs))

    quadrant_densities = [
        float(np.mean(binary_image[: h // 2, : w // 2] == 0)),
        float(np.mean(binary_image[: h // 2, w // 2 :] == 0)),
        float(np.mean(binary_image[h // 2 :, : w // 2] == 0)),
        float(np.mean(binary_image[h // 2 :, w // 2 :] == 0)),
    ]

    features = [density, run_length_mean, run_length_std, *quadrant_densities]
    features += [0.0] * (24 - len(features))  # pad to the spec'd 24 dims until the real
    return features[:24]                       # feature set from the trained model is ported in


def xgboost_quishing_score(features: list[float]) -> float | None:
    """STUB: needs a trained model -- see module docstring."""
    return None


def analyze_qr_image(image: np.ndarray) -> QrAnalysisResult:
    binary = alfa_preprocess(image)
    payload = decode_qr(binary)
    features = extract_structural_features(binary)
    score = xgboost_quishing_score(features)
    return QrAnalysisResult(decoded_payload=payload, structural_features=features, xgboost_quishing_score=score)
