"""Live website save/edit/delete verification against actual Delta tables.

Creates only one uniquely identified synthetic expense and removes it afterward.
Run with the website and backend already running.
"""
import sys
import time
import uuid
from datetime import datetime, timezone
from pathlib import Path
import httpx

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from app.core.database import SessionLocal
from app.models.databricks_outbox import DatabricksOutbox
from app.services.databricks_sync import DatabricksWriter


def await_delivery(record_id):
    deadline = time.monotonic() + 180
    while time.monotonic() < deadline:
        with SessionLocal() as db:
            queued = db.get(DatabricksOutbox, record_id)
            if queued is None:
                return
            if queued.last_error:
                raise RuntimeError("Delivery failed: " + queued.last_error)
        time.sleep(2)
    raise TimeoutError("Website expense did not finish Databricks delivery in 180 seconds")


def verify(writer, record, reason):
    for table, key in (("bronze_expenses", "id"), ("silver_expenses", "expense_id")):
        result = writer.execute(
            f"SELECT amount, reason FROM {writer.prefix}.`{table}` WHERE {key} = :id",
            [{"name": "id", "type": "STRING", "value": record["id"]}])
        rows = result.get("result", {}).get("data_array", [])
        assert len(rows) == 1, f"Expected exactly one row in {table}, got {len(rows)}"
        assert float(rows[0][0]) == record["amount"], table
        assert rows[0][1] == reason, table
        print("Verified", table, "amount and reason", flush=True)


if __name__ == "__main__":
    writer = DatabricksWriter()
    base = "http://127.0.0.1:3000/api/v1/expenses/"
    with httpx.Client(timeout=30) as client:
        record = None
        try:
            payload = dict(amount=0.01, merchant="WEB-SYNC-CHECK-" + uuid.uuid4().hex,
                reason="Website verification: 'tea'\nand lunch", category="Others",
                source="Manual", transaction_id="web-test-" + uuid.uuid4().hex,
                currency="INR", is_pending=False, transaction_date=datetime.now(timezone.utc).isoformat())
            response = client.post(base, json=payload)
            response.raise_for_status()
            record = response.json()
            print("Saved through website API:", record["id"], flush=True)
            await_delivery(record["id"])
            verify(writer, record, payload["reason"])
            response = client.put(base + record["id"], json={"reason": "Updated on website"})
            response.raise_for_status()
            await_delivery(record["id"])
            verify(writer, record, "Updated on website")
        finally:
            try:
                if record:
                    response = client.delete(base + record["id"])
                    response.raise_for_status()
                    await_delivery(record["id"])
                    for table, key in (("bronze_expenses", "id"), ("silver_expenses", "expense_id")):
                        result = writer.execute(f"SELECT COUNT(*) FROM {writer.prefix}.`{table}` WHERE {key} = :id",
                            [{"name": "id", "type": "STRING", "value": record["id"]}])
                        assert int(result["result"]["data_array"][0][0]) == 0, table
                    print("Removed synthetic expense from backend and both remote tables", flush=True)
            finally:
                writer.client.close()
    print("PASS: website save, edit, and delete reached actual Databricks Bronze/Silver", flush=True)
