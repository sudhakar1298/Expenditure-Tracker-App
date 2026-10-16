import base64
import hashlib
import re
import secrets
import time
import uuid
from datetime import datetime, timedelta, timezone

from cryptography.exceptions import InvalidSignature
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi import Depends, HTTPException, Request, status
from sqlalchemy.exc import IntegrityError
from sqlalchemy.orm import Session

from app.core.config import settings
from app.core.database import get_db
from app.models.device_credential import DeviceCredential, DeviceNonce


DEVICE_ID_PATTERN = re.compile(r"^[A-Za-z0-9._:-]{16,128}$")
NONCE_PATTERN = re.compile(r"^[A-Za-z0-9_-]{16,128}$")


def load_device_public_key(encoded_key: str) -> ec.EllipticCurvePublicKey:
    try:
        key_bytes = base64.b64decode(encoded_key, validate=True)
        public_key = serialization.load_der_public_key(key_bytes)
    except (ValueError, TypeError) as error:
        raise ValueError("Invalid public key encoding") from error
    if not isinstance(public_key, ec.EllipticCurvePublicKey) or not isinstance(
        public_key.curve, ec.SECP256R1
    ):
        raise ValueError("Public key must be an EC P-256 key")
    return public_key


def request_target(request: Request) -> str:
    raw_path = request.scope.get("raw_path", request.url.path.encode("utf-8"))
    query = request.scope.get("query_string", b"")
    target = raw_path.decode("ascii")
    if query:
        target += "?" + query.decode("ascii")
    return target


def canonical_request(
    method: str, target: str, timestamp: str, nonce: str, body: bytes
) -> bytes:
    body_hash = hashlib.sha256(body).hexdigest()
    return "\n".join(
        (method.upper(), target, timestamp, nonce, body_hash)
    ).encode("utf-8")


async def require_paired_device(
    request: Request, db: Session = Depends(get_db)
) -> str | None:
    if not settings.DEVICE_AUTH_ENABLED:
        return None

    device_id = request.headers.get("X-Device-ID", "")
    timestamp_text = request.headers.get("X-Timestamp", "")
    nonce = request.headers.get("X-Nonce", "")
    signature_text = request.headers.get("X-Signature", "")
    if not all((device_id, timestamp_text, nonce, signature_text)):
        raise HTTPException(
            status_code=status.HTTP_401_UNAUTHORIZED,
            detail="A paired device signature is required",
        )
    if not DEVICE_ID_PATTERN.fullmatch(device_id) or not NONCE_PATTERN.fullmatch(nonce):
        raise HTTPException(status_code=401, detail="Invalid device authentication headers")
    try:
        timestamp = int(timestamp_text)
    except ValueError as error:
        raise HTTPException(status_code=401, detail="Invalid request timestamp") from error
    if abs(int(time.time()) - timestamp) > settings.DEVICE_SIGNATURE_MAX_AGE_SECONDS:
        raise HTTPException(status_code=401, detail="Request signature has expired")

    device = db.get(DeviceCredential, device_id)
    if device is None:
        raise HTTPException(status_code=401, detail="Device is not paired")
    try:
        signature = base64.b64decode(signature_text, validate=True)
        public_key = load_device_public_key(device.public_key_der)
        body = await request.body()
        public_key.verify(
            signature,
            canonical_request(
                request.method, request_target(request), timestamp_text, nonce, body
            ),
            ec.ECDSA(hashes.SHA256()),
        )
    except (ValueError, InvalidSignature):
        raise HTTPException(status_code=401, detail="Invalid device signature")

    cutoff = datetime.now(timezone.utc) - timedelta(
        seconds=settings.DEVICE_SIGNATURE_MAX_AGE_SECONDS * 2
    )
    db.query(DeviceNonce).filter(DeviceNonce.created_at < cutoff).delete(
        synchronize_session=False
    )
    db.add(DeviceNonce(id=str(uuid.uuid4()), device_id=device_id, nonce=nonce))
    device.last_seen_at = datetime.now(timezone.utc)
    try:
        db.commit()
    except IntegrityError as error:
        db.rollback()
        raise HTTPException(status_code=401, detail="Request signature was already used") from error
    return device_id


def pairing_code_matches(candidate: str) -> bool:
    configured = settings.DEVICE_PAIRING_CODE
    return bool(configured) and secrets.compare_digest(
        candidate.encode("utf-8"), configured.encode("utf-8")
    )
