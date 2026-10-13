from sqlalchemy.orm import Session
from app.models.expense import Expense
from app.schemas.expense import ExpenseCreate
from datetime import timedelta

def is_duplicate_expense(db: Session, expense: ExpenseCreate) -> bool:
    """
    Checks if a matching expense (same amount, similar merchant, within 5 minutes) already exists.
    """
    if expense.transaction_id:
        existing = db.query(Expense).filter(Expense.transaction_id == expense.transaction_id).first()
        if existing:
            return True

    # Ensure naive datetime for DB filter comparison
    dt = expense.transaction_date
    if dt and dt.tzinfo is not None:
        dt = dt.replace(tzinfo=None)

    window_start = dt - timedelta(minutes=5)
    window_end = dt + timedelta(minutes=5)

    existing_matches = db.query(Expense).filter(
        Expense.amount == expense.amount,
        Expense.merchant.ilike(expense.merchant.strip())
    ).all()

    for item in existing_matches:
        item_dt = item.transaction_date
        if item_dt and item_dt.tzinfo is not None:
            item_dt = item_dt.replace(tzinfo=None)
        if window_start <= item_dt <= window_end:
            return True

    return False
