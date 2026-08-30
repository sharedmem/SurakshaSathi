"""SPARE (Secure PWA Anti-Replication Engine) -- server-side half of the handshake. The PWA
client encrypts {timestamp, device_fingerprint} with the server's RSA public key and appends them
as query params; this module decrypts, then applies the temporal gate (<=1 min skew) and compares
the device fingerprint against the session directory to detect a WebView replication wrapper.

Client-side (PWA JS, out of scope for this backend) generates the keypair-encrypted query string;
only the server-side decrypt + validate logic lives here.
"""

import base64
from dataclasses import dataclass
from datetime import datetime, timedelta, timezone

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding, rsa

REPLAY_WINDOW = timedelta(minutes=1)


@dataclass
class SpareValidationResult:
    valid: bool
    reason: str


def generate_server_keypair() -> tuple[rsa.RSAPrivateKey, rsa.RSAPublicKey]:
    """Run once at deploy time; private key stays in Secret Manager, public key ships to the PWA."""
    private_key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
    return private_key, private_key.public_key()


def decrypt_token(private_key: rsa.RSAPrivateKey, encrypted_b64: str) -> str:
    ciphertext = base64.urlsafe_b64decode(encrypted_b64)
    plaintext = private_key.decrypt(
        ciphertext,
        padding.OAEP(mgf=padding.MGF1(algorithm=hashes.SHA256()), algorithm=hashes.SHA256(), label=None),
    )
    return plaintext.decode("utf-8")


def validate_handshake(
    private_key: rsa.RSAPrivateKey,
    encrypted_timestamp_b64: str,
    encrypted_device_id_b64: str,
    known_session_device_id: str | None,
) -> SpareValidationResult:
    try:
        client_ts = datetime.fromisoformat(decrypt_token(private_key, encrypted_timestamp_b64))
        device_id = decrypt_token(private_key, encrypted_device_id_b64)
    except Exception:
        return SpareValidationResult(False, "decryption failed -- malformed or tampered token")

    now = datetime.now(timezone.utc)
    if abs(now - client_ts) > REPLAY_WINDOW:
        return SpareValidationResult(False, f"replay window exceeded: |{now} - {client_ts}| > 1 min")

    if known_session_device_id and device_id != known_session_device_id:
        return SpareValidationResult(
            False, "device fingerprint mismatch -- possible WebView replication wrapper"
        )

    return SpareValidationResult(True, "handshake valid")
