"""Isolated regression check: never writes to the user's expense database."""
import os
import sys
import unittest
from pathlib import Path
from unittest.mock import patch

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
os.environ["DATABASE_URL"] = "sqlite://"
from sqlalchemy import create_engine
from sqlalchemy.pool import StaticPool
from sqlalchemy.orm import sessionmaker
from fastapi.testclient import TestClient
from app.main import app
from app.core.database import Base, get_db
from databricks.config import config, fetch_expenses_from_api


class MobileDeliveryTest(unittest.TestCase):
    def test_save_retry_reason_and_paginated_pipeline_read(self):
        engine = create_engine("sqlite://", connect_args={"check_same_thread": False}, poolclass=StaticPool)
        Base.metadata.create_all(engine)
        Session = sessionmaker(bind=engine)
        def db():
            with Session() as session:
                yield session
        app.dependency_overrides[get_db] = db
        try:
            with TestClient(app) as client:
                payload = dict(amount=123.45, merchant='Test "merchant"', category="Food",
                    reason="Lunch\\tea\nwith friends", source="Manual", transaction_id="phone-test-1",
                    transaction_date="2026-10-02T15:00:00Z")
                first = client.post("/api/v1/expenses/", json=payload)
                self.assertEqual(first.status_code, 201)
                payload["reason"] = "Corrected reason"
                retry = client.post("/api/v1/expenses/", json=payload)
                self.assertEqual(retry.json()["id"], first.json()["id"])
                self.assertEqual(retry.json()["reason"], "Corrected reason")
                payload["transaction_id"] = "phone-test-2"
                second = client.post("/api/v1/expenses/", json=payload)
                self.assertNotEqual(second.json()["id"], first.json()["id"])
                self.assertEqual(len(client.get("/api/v1/expenses/").json()), 2)

                # Feed the actual API responses to the job's HTTP reader.
                import io
                from urllib.parse import urlsplit
                def fetch(request, timeout):
                    url = urlsplit(request.full_url)
                    response = client.get(url.path + "?" + url.query)
                    self.assertEqual(response.status_code, 200)
                    return io.BytesIO(response.content)
                with patch("databricks.config.urlopen", side_effect=fetch):
                    rows = fetch_expenses_from_api(limit=1)
                self.assertEqual(len(rows), 2)
                self.assertTrue(all(row["reason"] == "Corrected reason" for row in rows))
        finally:
            app.dependency_overrides.clear()
            engine.dispose()


if __name__ == "__main__":
    unittest.main()
