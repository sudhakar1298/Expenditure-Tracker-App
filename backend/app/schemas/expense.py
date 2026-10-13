from pydantic import BaseModel
from typing import Optional
from datetime import datetime

class ExpenseBase(BaseModel):
    amount: float
    merchant: str
    category: str = "Others"
    reason: Optional[str] = None
    bank: Optional[str] = None
    source: str = "SMS"
    raw_message: Optional[str] = None
    is_pending: bool = False
    transaction_date: datetime
    currency: str = "INR"

class ExpenseCreate(ExpenseBase):
    user_id: Optional[str] = None
    transaction_id: Optional[str] = None

class ExpenseUpdate(BaseModel):
    amount: Optional[float] = None
    merchant: Optional[str] = None
    category: Optional[str] = None
    reason: Optional[str] = None
    is_pending: Optional[bool] = None

class ExpenseResponse(ExpenseBase):
    id: str
    user_id: Optional[str] = None
    transaction_id: Optional[str] = None
    created_at: Optional[datetime] = None
    updated_at: Optional[datetime] = None

    class Config:
        from_attributes = True
