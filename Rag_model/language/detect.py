"""Language identification. Primary: Google Cloud Translation v2 `detect` endpoint (same
credential as translate.py, one less key to manage). Local fallback: `langdetect` (pure Python,
no model download) so identification still works if the Translate API circuit breaker is open.
"""

import httpx

from common.resilience import with_retry
from config.settings import settings

DETECT_V2_ENDPOINT = "https://translation.googleapis.com/language/translate/v2/detect"


@with_retry("google_translate_detect", exceptions=(httpx.HTTPError,))
def _call_google_detect(text: str) -> str:
    params = {"key": settings.google_translate_api_key, "q": text}
    resp = httpx.post(DETECT_V2_ENDPOINT, params=params, timeout=settings.url_head_timeout_ms / 1000)
    resp.raise_for_status()
    return resp.json()["data"]["detections"][0][0]["language"]


def _local_fallback_detect(text: str) -> str:
    from langdetect import detect, LangDetectException

    try:
        return detect(text)
    except LangDetectException:
        return "unknown"


def detect_language(text: str) -> str:
    if settings.google_translate_api_key:
        try:
            return _call_google_detect(text)
        except Exception:
            pass
    return _local_fallback_detect(text)
