import csv
import io
from datetime import datetime, timedelta
from typing import List
from sqlalchemy.orm import Session

from app.models.expense import Expense
from app.schemas.reconciliation import StatementRow, ReconciliationItem, ReconciliationResult

def categorize_description(desc: str) -> str:
    lower = desc.lower()
    if any(k in lower for k in ["swiggy", "zomato", "restaurant", "cafe", "food", "bakery", "dhaba", "canteen"]):
        return "Food"
    if any(k in lower for k in ["uber", "ola", "metro", "petrol", "fuel", "shell", "hpcl", "bpcl", "fastag", "irctc"]):
        return "Travel"
    if any(k in lower for k in ["amazon", "flipkart", "myntra", "store", "mart", "supermarket", "grocery"]):
        return "Shopping"
    if any(k in lower for k in ["bill", "electricity", "bescom", "airtel", "jio", "vi", "broadband", "recharge"]):
        return "Bills"
    if any(k in lower for k in ["netflix", "prime", "hotstar", "spotify", "cinema", "movie", "bookmyshow"]):
        return "Entertainment"
    return "Others"

def parse_bank_statement_csv(content_str: str) -> List[StatementRow]:
    rows: List[StatementRow] = []
    f = io.StringIO(content_str.strip())
    reader = csv.DictReader(f)

    for line in reader:
        # Standardize headers case-insensitively
        line_lower = {k.strip().lower(): v.strip() for k, v in line.items() if k}

        date_str = line_lower.get("date") or line_lower.get("txn date") or line_lower.get("transaction date")
        desc = line_lower.get("description") or line_lower.get("particulars") or line_lower.get("narration") or "Bank Transaction"
        debit_str = line_lower.get("debit") or line_lower.get("debit amount") or line_lower.get("amount") or "0"
        ref_no = line_lower.get("reference") or line_lower.get("ref no") or line_lower.get("chq/ref no")

        if not date_str or not debit_str:
            continue

        try:
            debit_val = float(debit_str.replace(",", "").replace("₹", "").replace("Rs", "").strip())
            if debit_val <= 0:
                continue

            # Parse ISO date or common DD/MM/YYYY formats
            parsed_date = None
            for fmt in ["%Y-%m-%d", "%d/%m/%Y", "%d-%m-%Y", "%Y/%m/%d"]:
                try:
                    parsed_date = datetime.strptime(date_str, fmt)
                    break
                except ValueError:
                    pass

            if not parsed_date:
                parsed_date = datetime.utcnow()

            rows.append(StatementRow(
                date=parsed_date,
                description=desc,
                debit_amount=debit_val,
                reference_no=ref_no
            ))
        except Exception:
            continue

    return rows

def reconcile_statement_with_expenses(db: Session, statement_rows: List[StatementRow]) -> ReconciliationResult:
    all_expenses = db.query(Expense).all()
    results: List[ReconciliationItem] = []

    matched_count = 0
    unmatched_count = 0

    for row in statement_rows:
        matched_exp = None

        # Try matching by exact amount + date within +/- 3 days
        for exp in all_expenses:
            dt_exp = exp.transaction_date.replace(tzinfo=None) if exp.transaction_date and exp.transaction_date.tzinfo else exp.transaction_date
            dt_row = row.date.replace(tzinfo=None) if row.date and row.date.tzinfo else row.date

            if abs((dt_exp - dt_row).days) <= 3 and abs(exp.amount - row.debit_amount) < 0.01:
                # Check string similarity
                if exp.merchant.lower() in row.description.lower() or row.description.lower() in exp.merchant.lower():
                    matched_exp = exp
                    break

        if matched_exp:
            matched_count += 1
            results.append(ReconciliationItem(
                statement_row=row,
                matched_expense_id=matched_exp.id,
                matched_merchant=matched_exp.merchant,
                match_status="EXACT_MATCH",
                suggested_category=matched_exp.category
            ))
        else:
            unmatched_count += 1
            category = categorize_description(row.description)
            results.append(ReconciliationItem(
                statement_row=row,
                match_status="UNMATCHED",
                suggested_category=category
            ))

    return ReconciliationResult(
        total_statement_items=len(statement_rows),
        matched_count=matched_count,
        unmatched_count=unmatched_count,
        items=results
    )
