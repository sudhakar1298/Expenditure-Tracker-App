from fastapi import APIRouter, Depends, HTTPException, status
from sqlalchemy.orm import Session
from typing import List, Optional
from datetime import datetime, timedelta

from app.core.database import get_db
from app.models.expense import Expense
from app.schemas.expense import ExpenseCreate, ExpenseResponse, ExpenseUpdate
from app.services.duplicate_detector import is_duplicate_expense
from app.services.databricks_sync import queue_expense

router = APIRouter()

@router.post("/", response_model=ExpenseResponse, status_code=status.HTTP_201_CREATED)
def create_expense(expense_in: ExpenseCreate, db: Session = Depends(get_db)):
    # Retries from the phone must be safe. The Android client sends a stable
    # transaction_id so a timeout cannot create a second copy.
    if expense_in.transaction_id:
        existing_by_id = db.query(Expense).filter(
            Expense.transaction_id == expense_in.transaction_id
        ).first()
        if existing_by_id:
            # A pending SMS can be posted first and finalized later. Refresh
            # the mutable fields while retaining the same server record.
            existing_by_id.amount = expense_in.amount
            existing_by_id.merchant = expense_in.merchant
            existing_by_id.category = expense_in.category
            existing_by_id.reason = expense_in.reason
            existing_by_id.source = expense_in.source
            existing_by_id.raw_message = expense_in.raw_message
            existing_by_id.is_pending = expense_in.is_pending
            existing_by_id.transaction_date = expense_in.transaction_date
            queue_expense(db, existing_by_id)
            db.commit()
            db.refresh(existing_by_id)
            return existing_by_id

    # Check duplicate
    if not expense_in.transaction_id and is_duplicate_expense(db, expense_in):
        existing = db.query(Expense).filter(
            Expense.amount == expense_in.amount,
            Expense.merchant.ilike(expense_in.merchant.strip())
        ).first()
        if existing:
            queue_expense(db, existing)
            db.commit()
            return existing

    db_expense = Expense(
        user_id=expense_in.user_id,
        transaction_id=expense_in.transaction_id,
        amount=expense_in.amount,
        currency=expense_in.currency,
        merchant=expense_in.merchant,
        category=expense_in.category,
        reason=expense_in.reason,
        bank=expense_in.bank,
        source=expense_in.source,
        raw_message=expense_in.raw_message,
        is_pending=expense_in.is_pending,
        transaction_date=expense_in.transaction_date or datetime.utcnow()
    )
    db.add(db_expense)
    queue_expense(db, db_expense)
    db.commit()
    db.refresh(db_expense)
    return db_expense

@router.get("/", response_model=List[ExpenseResponse])
def get_expenses(
    skip: int = 0,
    limit: int = 100,
    category: Optional[str] = None,
    source: Optional[str] = None,
    db: Session = Depends(get_db)
):
    query = db.query(Expense)
    if category:
        query = query.filter(Expense.category == category)
    if source:
        query = query.filter(Expense.source == source)
    return query.order_by(Expense.transaction_date.desc(), Expense.id).offset(skip).limit(limit).all()

@router.delete("/by-transaction/{transaction_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_expense_by_transaction_id(transaction_id: str, db: Session = Depends(get_db)):
    """Delete a phone-created expense using its stable client transaction ID."""
    expense = db.query(Expense).filter(Expense.transaction_id == transaction_id).first()
    if not expense:
        raise HTTPException(status_code=404, detail="Expense not found")
    queue_expense(db, expense, "delete")
    db.delete(expense)
    db.commit()
    return None

@router.get("/{expense_id}", response_model=ExpenseResponse)
def get_expense(expense_id: str, db: Session = Depends(get_db)):
    expense = db.query(Expense).filter(Expense.id == expense_id).first()
    if not expense:
        raise HTTPException(status_code=404, detail="Expense not found")
    return expense

@router.put("/{expense_id}", response_model=ExpenseResponse)
def update_expense(expense_id: str, expense_in: ExpenseUpdate, db: Session = Depends(get_db)):
    expense = db.query(Expense).filter(Expense.id == expense_id).first()
    if not expense:
        raise HTTPException(status_code=404, detail="Expense not found")

    update_data = expense_in.model_dump(exclude_unset=True)
    for field, value in update_data.items():
        setattr(expense, field, value)

    queue_expense(db, expense)
    db.commit()
    db.refresh(expense)
    return expense

@router.delete("/{expense_id}", status_code=status.HTTP_204_NO_CONTENT)
def delete_expense(expense_id: str, db: Session = Depends(get_db)):
    expense = db.query(Expense).filter(Expense.id == expense_id).first()
    if not expense:
        raise HTTPException(status_code=404, detail="Expense not found")
    db.delete(expense)
    queue_expense(db, expense, "delete")
    db.commit()
    return None
