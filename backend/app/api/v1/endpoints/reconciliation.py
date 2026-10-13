from fastapi import APIRouter, Depends, HTTPException, status, Body
from sqlalchemy.orm import Session
from typing import List, Optional
from pydantic import BaseModel

from app.core.database import get_db
from app.models.expense import Expense
from app.schemas.reconciliation import ReconciliationResult, ConfirmMissingPayload
from app.schemas.expense import ExpenseResponse
from app.services.reconciliation import parse_bank_statement_csv, reconcile_statement_with_expenses
from app.services.databricks_sync import queue_expense

router = APIRouter()

class UploadStatementTextRequest(BaseModel):
    statement_text: str

@router.post("/upload", response_model=ReconciliationResult)
def upload_bank_statement_text(payload: UploadStatementTextRequest, db: Session = Depends(get_db)):
    if not payload.statement_text or not payload.statement_text.strip():
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Statement text content is empty."
        )

    rows = parse_bank_statement_csv(payload.statement_text)
    if not rows:
        raise HTTPException(
            status_code=status.HTTP_400_BAD_REQUEST,
            detail="Failed to parse any valid debit transaction rows from statement content."
        )

    result = reconcile_statement_with_expenses(db, rows)
    return result

@router.post("/confirm", response_model=List[ExpenseResponse])
def confirm_missing_transactions(payload: ConfirmMissingPayload, db: Session = Depends(get_db)):
    created_expenses: List[Expense] = []

    for item in payload.items_to_create:
        if item.match_status == "UNMATCHED":
            new_exp = Expense(
                amount=item.statement_row.debit_amount,
                currency="INR",
                merchant=item.statement_row.description.strip(),
                category=item.suggested_category or "Others",
                source="Bank Statement",
                raw_message=f"Reconciled Ref: {item.statement_row.reference_no or 'N/A'}",
                is_pending=False,
                transaction_date=item.statement_row.date
            )
            db.add(new_exp)
            created_expenses.append(new_exp)

    for expense in created_expenses:
        queue_expense(db, expense)
    db.commit()
    for exp in created_expenses:
        db.refresh(exp)

    return created_expenses
