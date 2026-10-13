import json
import os
import sys

root_dir = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))
backend_dir = os.path.join(root_dir, "backend")

if root_dir not in sys.path:
    sys.path.insert(0, root_dir)
if backend_dir not in sys.path:
    sys.path.insert(0, backend_dir)

from app.core.database import SessionLocal
from app.models.expense import Expense

def export_all_data():
    db = SessionLocal()
    expenses = db.query(Expense).all()
    db.close()

    raw_data = [
        {
            "id": exp.id,
            "amount": exp.amount,
            "merchant": exp.merchant,
            "category": exp.category,
            "source": exp.source,
            "transaction_date": exp.transaction_date.isoformat() if exp.transaction_date else ""
        }
        for exp in expenses
    ]

    export_path = os.path.join(os.path.dirname(__file__), "all_expenses_export.json")
    with open(export_path, "w", encoding="utf-8") as f:
        json.dump(raw_data, f, indent=2)

    print(f"✓ Exported {len(raw_data)} expenses to {export_path}")

if __name__ == "__main__":
    export_all_data()
