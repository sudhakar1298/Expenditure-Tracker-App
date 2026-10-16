from fastapi import Depends, FastAPI
from fastapi.middleware.cors import CORSMiddleware
from sqlalchemy import func, text

from app.core.config import settings
from app.core.database import engine, Base
from app.models import user, expense, device_credential
from app.core.device_auth import require_paired_device
from app.api.v1.router import api_router
from contextlib import asynccontextmanager
from app.core.database import SessionLocal
from app.core.databricks_config import databricks_settings
from app.models.databricks_outbox import DatabricksOutbox
from app.services.databricks_sync import start_worker


@asynccontextmanager
async def lifespan(app):
    stop, thread = start_worker(SessionLocal)
    try:
        yield
    finally:
        stop.set()
        if thread:
            thread.join(timeout=1)

# Create database tables automatically
Base.metadata.create_all(bind=engine)

app = FastAPI(
    lifespan=lifespan,
    title=settings.PROJECT_NAME,
    openapi_url=f"{settings.API_V1_STR}/openapi.json"
)

# Enable CORS for Next.js web dashboard and Android local testing
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(api_router, prefix=settings.API_V1_STR)


@app.get("/api/v1/databricks/status")
def databricks_status(_device_id: str | None = Depends(require_paired_device)):
    with SessionLocal() as db:
        errors = dict(db.query(
            DatabricksOutbox.last_error,
            func.count(DatabricksOutbox.expense_id),
        ).filter(
            DatabricksOutbox.last_error.is_not(None)
        ).group_by(DatabricksOutbox.last_error).all())
        return {"enabled": databricks_settings.DATABRICKS_SYNC_ENABLED,
                "pending": db.query(DatabricksOutbox).count(),
                "failed_attempts": db.query(DatabricksOutbox).filter(DatabricksOutbox.attempts > 0).count(),
                "errors": errors}

@app.get("/")
def root():
    return {
        "message": "Welcome to GPay Expense Tracker API",
        "docs": "/docs",
        "version": "1.0.0"
    }


@app.get("/health", include_in_schema=False)
def health():
    """Render readiness check that also proves the operational DB is reachable."""
    with SessionLocal() as db:
        db.execute(text("SELECT 1"))
    return {"status": "ok"}
