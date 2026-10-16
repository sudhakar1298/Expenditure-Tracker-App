from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session

from app.core.database import get_db
from app.core.device_auth import (
    DEVICE_ID_PATTERN,
    load_device_public_key,
    pairing_code_matches,
    require_paired_device,
)
from app.models.device_credential import DeviceCredential
from app.schemas.device_auth import DevicePairRequest, DevicePairResponse


router = APIRouter()


@router.post(
    "/pair", response_model=DevicePairResponse, status_code=status.HTTP_201_CREATED
)
def pair_device(payload: DevicePairRequest, db: Session = Depends(get_db)):
    if not DEVICE_ID_PATTERN.fullmatch(payload.device_id):
        raise HTTPException(status_code=400, detail="Invalid device ID")
    try:
        load_device_public_key(payload.public_key_der)
    except ValueError as error:
        raise HTTPException(status_code=400, detail=str(error)) from error

    existing = db.get(DeviceCredential, payload.device_id)
    if existing and existing.public_key_der == payload.public_key_der:
        return DevicePairResponse(
            paired=True, device_id=existing.device_id, label=existing.label
        )
    if db.query(DeviceCredential).count() > 0:
        raise HTTPException(
            status_code=status.HTTP_409_CONFLICT,
            detail="A phone is already paired; revoke it before pairing another",
        )
    if not pairing_code_matches(payload.pairing_code):
        raise HTTPException(status_code=403, detail="Invalid pairing code")

    device = DeviceCredential(
        device_id=payload.device_id,
        public_key_der=payload.public_key_der,
        label=payload.label,
    )
    db.add(device)
    db.commit()
    return DevicePairResponse(
        paired=True, device_id=device.device_id, label=device.label
    )


@router.get("/status")
def device_status(device_id: str = Depends(require_paired_device)):
    return {"paired": True, "device_id": device_id}
