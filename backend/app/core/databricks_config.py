from pathlib import Path
import re
from urllib.parse import urlsplit
from pydantic import SecretStr, Field
from pydantic_settings import BaseSettings, SettingsConfigDict


class DatabricksSettings(BaseSettings):
    model_config = SettingsConfigDict(
        env_file=Path(__file__).resolve().parents[3] / "databricks" / ".env",
        env_file_encoding="utf-8", extra="ignore",
    )
    DATABRICKS_SYNC_ENABLED: bool = False
    DATABRICKS_HOST: str = ""
    DATABRICKS_TOKEN: SecretStr = SecretStr("")
    DATABRICKS_WAREHOUSE_ID: str = ""
    DATABRICKS_CATALOG: str = ""
    DATABRICKS_SCHEMA: str = "expense_tracker_dw"
    DATABRICKS_SYNC_INTERVAL_SECONDS: int = Field(default=15, ge=5)

    def validate_connection(self):
        required = ["DATABRICKS_HOST", "DATABRICKS_WAREHOUSE_ID", "DATABRICKS_CATALOG", "DATABRICKS_SCHEMA"]
        missing = [key for key in required if not getattr(self, key).strip()]
        if not self.DATABRICKS_TOKEN.get_secret_value().strip():
            missing.append("DATABRICKS_TOKEN")
        if missing:
            raise ValueError("Missing configuration: " + ", ".join(missing))
        host = urlsplit(self.DATABRICKS_HOST)
        if host.scheme != "https" or not host.hostname or host.username or host.query or host.fragment or host.path not in ("", "/"):
            raise ValueError("DATABRICKS_HOST must be an HTTPS workspace origin")
        for key in ("DATABRICKS_CATALOG", "DATABRICKS_SCHEMA"):
            if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", getattr(self, key)):
                raise ValueError(key + " must be a simple SQL identifier")


databricks_settings = DatabricksSettings()
