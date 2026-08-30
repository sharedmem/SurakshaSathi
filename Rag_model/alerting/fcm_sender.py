"""Server-side FCM push via firebase-admin. Requires FIREBASE_CREDENTIALS_PATH to point at a
real Firebase Admin SDK service-account JSON (Firebase Console -> Project Settings -> Service
Accounts -> Generate new private key) -- NOT the Android client's google-services.json, which is
a different file for a different purpose (client-side app config, not server-side send auth).
"""

import firebase_admin
from firebase_admin import credentials, messaging

from config.settings import settings

_initialized = False


def _ensure_initialized():
    global _initialized
    if _initialized:
        return
    if not settings.firebase_credentials_path:
        raise RuntimeError(
            "FIREBASE_CREDENTIALS_PATH not set to a valid service-account JSON -- "
            "see the note in .env.example / db/seed instructions"
        )
    cred = credentials.Certificate(settings.firebase_credentials_path)
    firebase_admin.initialize_app(cred)
    _initialized = True


def send_to_tokens(tokens: list[str], title: str, body: str, data: dict | None = None) -> int:
    """Returns count of successful sends. Used both for per-message warnings (Output 1) and daily
    regional alerts (Output 2) -- same transport, different trigger."""
    _ensure_initialized()
    if not tokens:
        return 0

    message = messaging.MulticastMessage(
        notification=messaging.Notification(title=title, body=body),
        data=data or {},
        tokens=tokens,
    )
    response = messaging.send_each_for_multicast(message)
    return response.success_count
