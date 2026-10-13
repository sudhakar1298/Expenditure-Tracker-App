from pydantic import BaseModel
from typing import List, Optional
from datetime import datetime

class StatementRow(BaseModel):
    date: datetime
    description: str
    debit_amount: float
    credit_amount: Optional[float] = 0.0
    reference_no: Optional[str] = None

class ReconciliationItem(BaseModel):
    statement_row: StatementRow
    matched_expense_id: Optional[str] = None
    matched_merchant: Optional[str] = None
    match_status: str  # EXACT_MATCH, UNMATCHED
    suggested_category: str = "Others"

class ReconciliationResult(BaseModel):
    total_statement_items: int
    matched_count: int
    unmatched_count: int
    items: List[ReconciliationItem]

class ConfirmMissingPayload(BaseModel):
    items_to_create: List[ReconciliationItem]
