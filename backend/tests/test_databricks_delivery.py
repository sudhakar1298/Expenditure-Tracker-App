import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch
from datetime import datetime
import json

os.environ["DATABASE_URL"] = "sqlite://"
os.environ["DATABRICKS_SYNC_ENABLED"] = "false"
sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
import httpx
from fastapi.testclient import TestClient
from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from sqlalchemy.pool import StaticPool
from app.main import app
from app.core.database import Base, get_db
from app.core.databricks_config import DatabricksSettings
from app.models.databricks_outbox import DatabricksOutbox
from app.models.expense import Expense
from app.services.databricks_sync import DatabricksWriter, deliver_pending


class DeliveryTests(unittest.TestCase):
    def setUp(self):
        self.engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        Base.metadata.create_all(self.engine)
        self.Session = sessionmaker(bind=self.engine)
        def db():
            with self.Session() as session:
                yield session
        app.dependency_overrides[get_db] = db
        self.client = TestClient(app)
        self.settings = DatabricksSettings(_env_file=None, DATABRICKS_HOST="https://example.databricks.com",
            DATABRICKS_TOKEN="test-secret", DATABRICKS_WAREHOUSE_ID="warehouse",
            DATABRICKS_CATALOG="main")

    def tearDown(self):
        self.client.close()
        app.dependency_overrides.clear()
        self.engine.dispose()

    def save(self):
        response = self.client.post("/api/v1/expenses/", json=dict(amount=42.5,
            merchant="Test merchant", reason="Lunch 'with' friends\nTea", transaction_id="phone-1",
            transaction_date="2026-10-02T12:00:00Z", source="Manual"))
        self.assertEqual(response.status_code, 201)
        return response.json()

    def writer(self, handler):
        return DatabricksWriter(self.settings, httpx.Client(transport=httpx.MockTransport(handler)))

    def test_save_is_durable_and_retry_merges_both_tables(self):
        record = self.save()
        self.save()
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).count(), 1)
        requests = []
        def handler(request):
            self.assertEqual(request.headers["Authorization"], "Bearer test-secret")
            requests.append(json.loads(request.content))
            return httpx.Response(200, json={"status": {"state": "SUCCEEDED"}})
        writer = self.writer(handler)
        deliver_pending(self.Session, writer)
        writer.client.close()
        self.assertEqual(len(requests), 2)
        self.assertIn("bronze_expenses", requests[0]["statement"])
        self.assertIn("silver_expenses", requests[1]["statement"])
        for request in requests:
            self.assertNotIn(record["reason"], request["statement"])
            params = {p["name"]: p["value"] for p in request["parameters"]}
            row = json.loads(params["rows"])[0]
            self.assertEqual(row["reason"], record["reason"])
            self.assertEqual(row["id"], record["id"])
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).count(), 0)

    def test_pending_sms_is_queued_only_after_confirmation(self):
        draft_payload = dict(amount=78.0, merchant="Pending Details", category="Other",
            transaction_id="sms-draft-1", transaction_date="2026-10-14T12:00:00Z",
            source="SMS", raw_message="Account debited Rs.78", is_pending=True)

        draft = self.client.post("/api/v1/expenses/", json=draft_payload)
        self.assertEqual(draft.status_code, 201)
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).count(), 0)

        confirmed_payload = dict(draft_payload, merchant="Cafe", category="Food & Dining",
            reason="Lunch", is_pending=False)
        confirmed = self.client.post("/api/v1/expenses/", json=confirmed_payload)
        self.assertEqual(confirmed.status_code, 201)
        self.assertEqual(confirmed.json()["id"], draft.json()["id"])
        with self.Session() as db:
            queued = db.query(DatabricksOutbox).one()
            payload = json.loads(queued.payload)
            self.assertEqual(payload["merchant"], "Cafe")
            self.assertEqual(payload["category"], "Food & Dining")
            self.assertEqual(payload["reason"], "Lunch")

    def test_delivery_discards_legacy_pending_draft(self):
        with self.Session() as db:
            expense = Expense(amount=10, merchant="Pending Details", category="Other",
                source="SMS", is_pending=True, transaction_date=datetime.utcnow())
            db.add(expense)
            db.flush()
            db.add(DatabricksOutbox(expense_id=expense.id, revision="legacy",
                operation="upsert", payload="{}", attempts=0))
            db.commit()

        class RejectWrites:
            def write_batch(self, rows, operation):
                raise AssertionError("A pending draft must not reach Databricks")

        deliver_pending(self.Session, RejectWrites())
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).count(), 0)

    def test_http_200_failed_statement_keeps_queue(self):
        self.save()
        writer = self.writer(lambda req: httpx.Response(200, json={"status": {"state": "FAILED"}}))
        deliver_pending(self.Session, writer)
        writer.client.close()
        with self.Session() as db:
            row = db.query(DatabricksOutbox).one()
            self.assertEqual(row.attempts, 1)
            self.assertGreater(row.next_attempt, datetime.utcnow())
            self.assertNotIn("test-secret", row.last_error)

    def test_fifty_expenses_use_two_remote_statements(self):
        for i in range(50):
            response = self.client.post("/api/v1/expenses/", json=dict(amount=10,
                merchant="Batch test", reason=None, transaction_id=f"batch-{i}",
                transaction_date="2026-10-02T12:00:00Z"))
            self.assertEqual(response.status_code, 201)
        requests = []
        def handler(request):
            requests.append(json.loads(request.content))
            return httpx.Response(200, json={"status": {"state": "SUCCEEDED"}})
        writer = self.writer(handler)
        deliver_pending(self.Session, writer)
        writer.client.close()
        self.assertEqual(len(requests), 2)
        self.assertEqual(len(json.loads(requests[0]["parameters"][0]["value"])), 50)
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).count(), 0)

    def test_asynchronous_statement_is_polled(self):
        calls = []
        def handler(request):
            calls.append(request.method)
            return httpx.Response(200, json={"statement_id": "abc", "status": {
                "state": "PENDING" if request.method == "POST" else "SUCCEEDED"}})
        writer = self.writer(handler)
        with patch("app.services.databricks_sync.time.sleep"):
            writer.execute("SELECT 1")
        writer.client.close()
        self.assertEqual(calls, ["POST", "GET"])

    def test_delete_replaces_queued_save(self):
        record = self.save()
        self.assertEqual(self.client.delete("/api/v1/expenses/" + record["id"]).status_code, 204)
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).one().operation, "delete")

    def test_auth_failure_retains_payload_without_secret_in_error(self):
        self.save()
        writer = self.writer(lambda req: httpx.Response(401, json={"message": "Unauthorized"}))
        deliver_pending(self.Session, writer)
        writer.client.close()
        with self.Session() as db:
            row = db.query(DatabricksOutbox).one()
            self.assertEqual(row.last_error,
                "Databricks HTTP 401 POST /api/2.0/sql/statements")
            self.assertEqual(json.loads(row.payload)["amount"], 42.5)

    def test_partial_remote_failure_retries_both_merges(self):
        self.save()
        calls = []
        def handler(request):
            calls.append(json.loads(request.content)["statement"])
            return httpx.Response(200, json={"status": {
                "state": "FAILED" if len(calls) == 2 else "SUCCEEDED"}})
        writer = self.writer(handler)
        deliver_pending(self.Session, writer)
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).count(), 1)
            db.query(DatabricksOutbox).update({"next_attempt": None})
            db.commit()
        deliver_pending(self.Session, writer)
        writer.client.close()
        self.assertEqual(calls[:2], calls[2:])
        with self.Session() as db:
            self.assertEqual(db.query(DatabricksOutbox).count(), 0)

    def test_unconfigured_connection_names_missing_fields(self):
        config = DatabricksSettings(_env_file=None)
        with self.assertRaisesRegex(ValueError, "DATABRICKS_TOKEN"):
            config.validate_connection()

    def test_old_delivery_cannot_acknowledge_new_edit(self):
        record = self.save()
        class ConcurrentWriter:
            def write_batch(inner, data, operation):
                response = self.client.put("/api/v1/expenses/" + record["id"], json={"reason": "Changed"})
                self.assertEqual(response.status_code, 200)
        deliver_pending(self.Session, ConcurrentWriter())
        with self.Session() as db:
            self.assertEqual(json.loads(db.query(DatabricksOutbox).one().payload)["reason"], "Changed")


if __name__ == "__main__":
    unittest.main()
