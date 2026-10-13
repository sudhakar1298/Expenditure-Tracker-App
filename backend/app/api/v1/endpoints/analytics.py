from fastapi import APIRouter, Depends
from sqlalchemy.orm import Session
from sqlalchemy import func
from typing import List
from datetime import datetime, timedelta

from app.core.database import get_db
from app.models.expense import Expense
from app.schemas.analytics import AnalyticsSummary, CategorySummary

router = APIRouter()

@router.get("/summary", response_model=AnalyticsSummary)
def get_analytics_summary(days: int = 30, db: Session = Depends(get_db)):
    start_date = datetime.utcnow() - timedelta(days=days)

    total = db.query(func.sum(Expense.amount)).filter(Expense.transaction_date >= start_date).scalar() or 0.0
    count = db.query(func.count(Expense.id)).filter(Expense.transaction_date >= start_date).scalar() or 0

    category_results = (
        db.query(
            Expense.category,
            func.sum(Expense.amount).label("total_amount"),
            func.count(Expense.id).label("count")
        )
        .filter(Expense.transaction_date >= start_date)
        .group_by(Expense.category)
        .all()
    )

    category_breakdown = [
        CategorySummary(
            category=row.category or "Others",
            total_amount=float(row.total_amount or 0.0),
            count=int(row.count or 0)
        )
        for row in category_results
    ]

    return AnalyticsSummary(
        total_spent=float(total),
        transaction_count=int(count),
        category_breakdown=category_breakdown,
        period=f"Last {days} days"
    )
