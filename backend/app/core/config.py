import os
from pydantic_settings import BaseSettings

_BASE_DIR = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
_SQLITE_FILE = os.path.join(_BASE_DIR, "expense_tracker.db")

class Settings(BaseSettings):
    PROJECT_NAME: str = "GPay Expense Tracker API"
    API_V1_STR: str = "/api/v1"

    POSTGRES_SERVER: str = os.getenv("POSTGRES_SERVER", "localhost")
    POSTGRES_USER: str = os.getenv("POSTGRES_USER", "postgres")
    POSTGRES_PASSWORD: str = os.getenv("POSTGRES_PASSWORD", "postgres")
    POSTGRES_DB: str = os.getenv("POSTGRES_DB", "expense_tracker")
    POSTGRES_PORT: str = os.getenv("POSTGRES_PORT", "5432")

    DATABASE_URL: str = os.getenv(
        "DATABASE_URL",
        f"postgresql://{POSTGRES_USER}:{POSTGRES_PASSWORD}@{POSTGRES_SERVER}:{POSTGRES_PORT}/{POSTGRES_DB}"
    )

    # SQLite fallback for local development or lightweight testing if Postgres is not running
    USE_SQLITE_FALLBACK: bool = os.getenv("USE_SQLITE_FALLBACK", "true").lower() == "true"
    SQLITE_URL: str = f"sqlite:///{_SQLITE_FILE}"

    # Device-bound request signing. Deploy with authentication disabled, pair
    # the phone once, then enable enforcement in Render.
    DEVICE_AUTH_ENABLED: bool = os.getenv("DEVICE_AUTH_ENABLED", "false").lower() == "true"
    DEVICE_PAIRING_CODE: str = os.getenv("DEVICE_PAIRING_CODE", "")
    DEVICE_SIGNATURE_MAX_AGE_SECONDS: int = int(
        os.getenv("DEVICE_SIGNATURE_MAX_AGE_SECONDS", "300")
    )

    class Config:
        case_sensitive = True

settings = Settings()
