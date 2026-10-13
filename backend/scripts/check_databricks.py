"""Check the same Databricks credentials used by the hosted sync worker."""
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))

from app.services.databricks_sync import DatabricksWriter


def main() -> None:
    writer = DatabricksWriter()
    try:
        writer.execute("SELECT 1")
    finally:
        writer.client.close()
    print("Databricks SQL connection succeeded.")


if __name__ == "__main__":
    main()
