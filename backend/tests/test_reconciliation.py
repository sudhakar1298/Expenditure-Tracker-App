import sys
import os

# Add backend directory to sys.path
sys.path.insert(0, os.path.abspath(os.path.join(os.path.dirname(__file__), "..")))

from sqlalchemy import create_engine
from sqlalchemy.orm import sessionmaker
from app.main import app
from app.core.database import Base, get_db
from fastapi.testclient import TestClient

TEST_DB_URL = "sqlite:///./test_expense_tracker.db"
test_engine = create_engine(TEST_DB_URL, connect_args={"check_same_thread": False})
TestingSessionLocal = sessionmaker(autocommit=False, autoflush=False, bind=test_engine)

Base.metadata.drop_all(bind=test_engine)
Base.metadata.create_all(bind=test_engine)

def override_get_db():
    db = TestingSessionLocal()
    try:
        yield db
    finally:
        db.close()

app.dependency_overrides[get_db] = override_get_db

def test_reconciliation_flow():
    client = TestClient(app)

    # 1. Pre-insert a known expense (Swiggy ₹450)
    exp_payload = {
        "amount": 450.0,
        "merchant": "Swiggy",
        "category": "Food",
        "source": "GPay",
        "transaction_date": "2026-10-01T12:00:00Z"
    }
    client.post("/api/v1/expenses/", json=exp_payload)

    # 2. Upload sample CSV bank statement
    csv_content = """Date,Description,Debit,Reference
2026-10-01,UPI-SWIGGY-REST,450.00,REF12345
2026-10-02,ACT FIBERNET BILL,999.00,REF67890
"""
    upload_res = client.post("/api/v1/reconciliation/upload", json={"statement_text": csv_content})
    assert upload_res.status_code == 200, f"Expected 200, got {upload_res.status_code}"

    result = upload_res.json()
    assert result["total_statement_items"] == 2
    assert result["matched_count"] == 1
    assert result["unmatched_count"] == 1

    # Verify matched item
    matched_item = [it for it in result["items"] if it["match_status"] == "EXACT_MATCH"][0]
    assert matched_item["matched_merchant"] == "Swiggy"

    # Verify unmatched item
    unmatched_item = [it for it in result["items"] if it["match_status"] == "UNMATCHED"][0]
    assert unmatched_item["statement_row"]["debit_amount"] == 999.00
    assert unmatched_item["suggested_category"] == "Bills"

    # 3. Confirm missing transaction
    confirm_payload = {
        "items_to_create": [unmatched_item]
    }
    confirm_res = client.post("/api/v1/reconciliation/confirm", json=confirm_payload)
    assert confirm_res.status_code == 200
    created_items = confirm_res.json()
    assert len(created_items) == 1
    assert created_items[0]["amount"] == 999.00
    assert created_items[0]["category"] == "Bills"

    print("✓ Bank statement reconciliation test passed successfully!")

if __name__ == "__main__":
    test_reconciliation_flow()
