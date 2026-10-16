from fastapi import APIRouter, Depends
from app.api.v1.endpoints import expenses, analytics, reconciliation, device_auth
from app.core.device_auth import require_paired_device

api_router = APIRouter()
api_router.include_router(device_auth.router, prefix="/auth/device", tags=["device-auth"])
protected = [Depends(require_paired_device)]
api_router.include_router(expenses.router, prefix="/expenses", tags=["expenses"], dependencies=protected)
api_router.include_router(analytics.router, prefix="/analytics", tags=["analytics"], dependencies=protected)
api_router.include_router(reconciliation.router, prefix="/reconciliation", tags=["reconciliation"], dependencies=protected)
