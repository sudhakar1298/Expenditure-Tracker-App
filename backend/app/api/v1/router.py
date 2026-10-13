from fastapi import APIRouter
from app.api.v1.endpoints import expenses, analytics, reconciliation

api_router = APIRouter()
api_router.include_router(expenses.router, prefix="/expenses", tags=["expenses"])
api_router.include_router(analytics.router, prefix="/analytics", tags=["analytics"])
api_router.include_router(reconciliation.router, prefix="/reconciliation", tags=["reconciliation"])
