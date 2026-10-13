import csv
import os
import sys
from datetime import datetime

# Add current directory to path
sys.path.insert(0, os.path.abspath(os.path.dirname(__file__)))

from app.core.database import SessionLocal, engine, Base
from app.models.expense import Expense

CSV_PATH = r"C:\Users\sudha\OneDrive\Desktop\expense tracker - Sheet1.csv"

def parse_date(date_str: str) -> datetime:
    formats = [
        "%m/%d/%Y %H:%M:%S",
        "%n/%j/%Y %H:%M:%S",
        "%d/%m/%Y %H:%M:%S",
        "%Y-%m-%d %H:%M:%S"
    ]
    for fmt in formats:
        try:
            return datetime.strptime(date_str.strip(), fmt)
        except ValueError:
            pass
    try:
        # Fallback to general parsing
        parts = date_str.strip().split(" ")
        date_parts = parts[0].split("/")
        time_parts = parts[1].split(":")
        month, day, year = int(date_parts[0]), int(date_parts[1]), int(date_parts[2])
        hour, minute, second = int(time_parts[0]), int(time_parts[1]), int(time_parts[2])
        return datetime(year, month, day, hour, minute, second)
    except Exception:
        return datetime.utcnow()

def import_csv_data():
    if not os.path.exists(CSV_PATH):
        print(f"CSV file not found at {CSV_PATH}")
        return

    Base.metadata.create_all(bind=engine)
    db = SessionLocal()

    inserted_count = 0
    skipped_count = 0

    with open(CSV_PATH, mode="r", encoding="utf-8") as f:
        reader = csv.reader(f)
        for row in reader:
            if not row or len(row) < 5:
                continue

            date_str = row[0].strip()
            amount_str = row[1].strip()
            merchant = row[2].strip()
            category = row[3].strip()
            source = row[4].strip()

            try:
                amount = float(amount_str)
                dt = parse_date(date_str)

                # Check duplicate
                existing = db.query(Expense).filter(
                    Expense.amount == amount,
                    Expense.merchant.ilike(merchant),
                    Expense.transaction_date == dt
                ).first()

                if existing:
                    skipped_count += 1
                    continue

                new_expense = Expense(
                    amount=amount,
                    currency="INR",
                    merchant=merchant or "Expense Item",
                    category=category or "Others",
                    source=source or "Manual",
                    is_pending=False,
                    transaction_date=dt
                )
                db.add(new_expense)
                inserted_count += 1
            except Exception as e:
                print(f"Error parsing row {row}: {e}")
                continue

    db.commit()
    db.close()
    print(f"\n✓ CSV Import Completed Successfully!")
    print(f"  - Inserted records: {inserted_count}")
    print(f"  - Skipped duplicates: {skipped_count}")

if __name__ == "__main__":
    import_csv_data()
