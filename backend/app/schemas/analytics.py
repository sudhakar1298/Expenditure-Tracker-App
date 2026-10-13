from pydantic import BaseModel
from typing import Dict, List, Optional

class CategorySummary(BaseModel):
    category: str
    total_amount: float
    count: int

class AnalyticsSummary(BaseModel):
    total_spent: float
    transaction_count: int
    category_breakdown: List[CategorySummary]
    period: str
