"""Configure tables/backfill explicitly, using credentials from databricks/.env."""
import argparse
import sys
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "backend"))
from app.services.databricks_sync import DatabricksWriter, queue_expense
from app.core.database import Base, engine, SessionLocal
from app.models import user, expense


def setup(writer):
    writer.execute(f"CREATE SCHEMA IF NOT EXISTS {writer.prefix}")
    tables = {
        "bronze_expenses": "id STRING, amount DOUBLE, merchant STRING, category STRING, source STRING, transaction_date STRING, ingestion_timestamp TIMESTAMP, data_quality_status STRING, reason STRING",
        "silver_expenses": "expense_id STRING, amount DOUBLE, merchant STRING, category STRING, source STRING, transaction_timestamp TIMESTAMP, reason STRING",
    }
    for name, schema in tables.items():
        table = f"{writer.prefix}.`{name}`"
        writer.execute(f"CREATE TABLE IF NOT EXISTS {table} ({schema}) USING DELTA")
        result = writer.execute(f"DESCRIBE TABLE {table}")
        columns = {row[0] for row in result.get("result", {}).get("data_array", [])}
        if not columns:
            raise RuntimeError("Could not inspect table columns; no migration performed")
        if "reason" not in columns:
            writer.execute(f"ALTER TABLE {table} ADD COLUMNS (reason STRING)")
    print("Bronze/Silver tables ready; existing rows preserved.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--setup", action="store_true", help="Create missing schema/tables and add reason columns")
    parser.add_argument("--backfill", action="store_true", help="Queue existing local expenses for delivery")
    args = parser.parse_args()
    writer = DatabricksWriter()
    try:
        writer.execute("SELECT 1")
        print("Databricks warehouse connection verified.")
        if args.setup:
            setup(writer)
        if args.backfill:
            Base.metadata.create_all(engine)
            with SessionLocal() as db:
                rows = db.query(expense.Expense).all()
                for row in rows:
                    queue_expense(db, row)
                db.commit()
                print(f"Queued {len(rows)} existing expenses. Enable sync and run the backend to deliver.")
    finally:
        writer.client.close()
