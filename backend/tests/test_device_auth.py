import base64
import hashlib
import json
import time
import uuid

from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool

from app.core.config import settings
from app.core.database import Base, get_db
from app.main import app


def signed_headers(private_key, device_id, method, target, body=b"", nonce=None):
    timestamp = str(int(time.time()))
    nonce = nonce or uuid.uuid4().hex
    canonical = "\n".join(
        (method, target, timestamp, nonce, hashlib.sha256(body).hexdigest())
    ).encode()
    signature = private_key.sign(canonical, ec.ECDSA(hashes.SHA256()))
    return {
        "X-Device-ID": device_id,
        "X-Timestamp": timestamp,
        "X-Nonce": nonce,
        "X-Signature": base64.b64encode(signature).decode(),
    }


def test_pair_sign_and_reject_replay():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine)

    def db_override():
        with session_factory() as session:
            yield session

    previous_enabled = settings.DEVICE_AUTH_ENABLED
    previous_code = settings.DEVICE_PAIRING_CODE
    settings.DEVICE_AUTH_ENABLED = True
    settings.DEVICE_PAIRING_CODE = "correct-horse-battery-staple"
    app.dependency_overrides[get_db] = db_override
    private_key = ec.generate_private_key(ec.SECP256R1())
    public_der = private_key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    device_id = "android-" + uuid.uuid4().hex

    try:
        with TestClient(app) as client:
            assert client.get("/api/v1/expenses/").status_code == 401
            pair = client.post(
                "/api/v1/auth/device/pair",
                json={
                    "device_id": device_id,
                    "public_key_der": base64.b64encode(public_der).decode(),
                    "pairing_code": "correct-horse-battery-staple",
                    "label": "Test phone",
                },
            )
            assert pair.status_code == 201

            payload = {
                "amount": 25.0,
                "merchant": "Signed purchase",
                "category": "Other",
                "source": "Manual",
                "transaction_date": "2026-10-16T12:00:00Z",
            }
            body = json.dumps(payload, separators=(",", ":")).encode()
            headers = signed_headers(
                private_key, device_id, "POST", "/api/v1/expenses/", body
            )
            headers["Content-Type"] = "application/json"
            created = client.post(
                "/api/v1/expenses/", content=body, headers=headers
            )
            assert created.status_code == 201

            replay = client.post(
                "/api/v1/expenses/", content=body, headers=headers
            )
            assert replay.status_code == 401
            assert replay.json()["detail"] == "Request signature was already used"

            target = "/api/v1/expenses/?skip=0&limit=100"
            get_headers = signed_headers(private_key, device_id, "GET", target)
            listed = client.get(
                "/api/v1/expenses/?skip=0&limit=100", headers=get_headers
            )
            assert listed.status_code == 200
            assert len(listed.json()) == 1
    finally:
        settings.DEVICE_AUTH_ENABLED = previous_enabled
        settings.DEVICE_PAIRING_CODE = previous_code
        app.dependency_overrides.clear()
        engine.dispose()


def test_pairing_code_and_key_are_validated():
    engine = create_engine(
        "sqlite://",
        connect_args={"check_same_thread": False},
        poolclass=StaticPool,
    )
    Base.metadata.create_all(engine)
    session_factory = sessionmaker(bind=engine)

    def db_override():
        with session_factory() as session:
            yield session

    previous_code = settings.DEVICE_PAIRING_CODE
    settings.DEVICE_PAIRING_CODE = "correct-horse-battery-staple"
    app.dependency_overrides[get_db] = db_override
    private_key = ec.generate_private_key(ec.SECP256R1())
    public_der = private_key.public_key().public_bytes(
        serialization.Encoding.DER,
        serialization.PublicFormat.SubjectPublicKeyInfo,
    )
    try:
        with TestClient(app) as client:
            response = client.post(
                "/api/v1/auth/device/pair",
                json={
                    "device_id": "android-" + uuid.uuid4().hex,
                    "public_key_der": base64.b64encode(public_der).decode(),
                    "pairing_code": "this-code-is-definitely-wrong",
                },
            )
            assert response.status_code == 403
    finally:
        settings.DEVICE_PAIRING_CODE = previous_code
        app.dependency_overrides.clear()
        engine.dispose()
