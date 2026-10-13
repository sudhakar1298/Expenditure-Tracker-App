"""Create one uniquely identified probe through the website, verify, then remove it."""
import json
import sys
import uuid
from datetime import datetime, timezone
from pathlib import Path
from urllib.request import Request, urlopen

sys.path.insert(0, str(Path(__file__).resolve().parents[2]))
from databricks.config import config, fetch_expenses_from_api


def request(url, method="GET", payload=None):
    body = json.dumps(payload).encode() if payload is not None else None
    with urlopen(Request(url, data=body, method=method,
                         headers={"Content-Type": "application/json"}), timeout=20) as response:
        data = response.read()
        return json.loads(data) if data else None


if __name__ == "__main__":
    base = "http://127.0.0.1:3000/api/v1/expenses/"
    payload = dict(amount=0.01, merchant="Delivery verification " + uuid.uuid4().hex,
                   reason='Probe "reason" with newline\nverified', category="Test",
                   transaction_id="probe-" + uuid.uuid4().hex, source="Manual",
                   transaction_date=datetime.now(timezone.utc).isoformat())
    record = None
    try:
        record = request(base, "POST", payload)
        assert record["reason"] == payload["reason"]
        assert request(base + record["id"])["amount"] == 0.01
        assert request(base, "POST", payload)["id"] == record["id"]
        assert any(row["id"] == record["id"] for row in request(base))
        config.API_SOURCE_URL = "http://127.0.0.1:8000/api/v1/expenses/"
        rows = fetch_expenses_from_api(limit=50)
        found = next(row for row in rows if row["id"] == record["id"])
        assert found["reason"] == payload["reason"]
        print("PASS: live website proxy POST, reason, retry, website read, and Databricks API reader")
    finally:
        if record:
            request(base + record["id"], "DELETE")
            print("Removed only this run's synthetic probe:", record["id"])
