import pytest
from fastapi.testclient import TestClient
from datetime import datetime

from app.main import app

client = TestClient(app)

def test_root():
    response = client.get("/")
    assert response.status_code == 200
    assert "message" in response.json()

def test_create_and_get_expense():
    payload = {
        "amount": 450.0,
        "merchant": "Swiggy",
        "category": "Food",
        "source": "GPay",
        "transaction_date": datetime.utcnow().isoformat()
    }
    create_res = client.post("/api/v1/expenses/", json=payload)
    assert create_res.status_code == 201
    data = create_res.json()
    assert data["amount"] == 450.0
    assert data["merchant"] == "Swiggy"
    assert "id" in data

    # Test duplicate detection
    dup_res = client.post("/api/v1/expenses/", json=payload)
    assert dup_res.status_code == 201
    assert dup_res.json()["id"] == data["id"] # Should return existing match

    # Test get all expenses
    get_res = client.get("/api/v1/expenses/")
    assert get_res.status_code == 200
    items = get_res.json()
    assert len(items) >= 1

def test_analytics_summary():
    res = client.get("/api/v1/analytics/summary")
    assert res.status_code == 200
    summary = res.json()
    assert "total_spent" in summary
    assert "category_breakdown" in summary
