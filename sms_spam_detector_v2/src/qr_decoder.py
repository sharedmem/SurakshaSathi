"""
QR Decoder — server-side fallback for the web test UI.
========================================================
On a real Android device, QR payloads are decoded ON-DEVICE by Google
ML Kit's Barcode Scanning API (see README "Android QR Scanning" section) —
the phone never uploads the raw QR image, only the decoded text, which
arrives here as metadata["qr_decoded_text"]. That is the ONLY path used by
SMSAnalyzer / FeatureExtractor.

This module exists purely so the Flask web UI (src/app.py) can demo the
same flow from a browser/desktop without a phone: upload a QR image,
decode it locally with OpenCV, and drop the decoded text into the same
"qr_decoded_text" field a phone would have sent.

No cloud call, no extra model weight — OpenCV's QRCodeDetector is a
classical (non-ML) decoder bundled with opencv-python(-headless).
"""

import base64
from typing import List, Optional

import numpy as np

try:
    import cv2
    _CV2 = True
except ImportError:
    _CV2 = False


def decode_qr_bytes(image_bytes: bytes) -> List[str]:
    """Decode all QR codes found in an image. Returns a list of decoded strings."""
    if not _CV2:
        raise RuntimeError("opencv-python (cv2) is not installed — cannot decode QR images")
    arr = np.frombuffer(image_bytes, dtype=np.uint8)
    img = cv2.imdecode(arr, cv2.IMREAD_COLOR)
    if img is None:
        return []
    detector = cv2.QRCodeDetector()
    payloads: List[str] = []
    try:
        ok, decoded_info, _points, _straight = detector.detectAndDecodeMulti(img)
        if ok:
            payloads = [d for d in decoded_info if d]
    except Exception:
        pass
    if not payloads:
        text, _points, _straight = detector.detectAndDecode(img)
        if text:
            payloads = [text]
    return payloads


def decode_qr_base64(data_url_or_b64: str) -> List[str]:
    """Accepts a raw base64 string or a `data:image/png;base64,...` data URL."""
    b64 = data_url_or_b64.split(",", 1)[-1] if "," in data_url_or_b64 else data_url_or_b64
    image_bytes = base64.b64decode(b64)
    return decode_qr_bytes(image_bytes)


def decode_qr_file(path: str) -> List[str]:
    with open(path, "rb") as f:
        return decode_qr_bytes(f.read())


if __name__ == "__main__":
    import argparse, json
    ap = argparse.ArgumentParser(description="Decode QR code(s) from an image file")
    ap.add_argument("image_path")
    args = ap.parse_args()
    print(json.dumps(decode_qr_file(args.image_path), indent=2, ensure_ascii=False))
