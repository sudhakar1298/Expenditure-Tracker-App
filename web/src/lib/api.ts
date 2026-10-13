export interface Expense {
  id: string;
  user_id?: string;
  transaction_id?: string;
  amount: number;
  currency: string;
  merchant: string;
  category: string;
  reason?: string;
  bank?: string;
  source: string;
  raw_message?: string;
  is_pending: boolean;
  transaction_date: string;
  created_at?: string;
}

export interface CategorySummary {
  category: string;
  total_amount: number;
  count: number;
}

export interface AnalyticsSummary {
  total_spent: number;
  transaction_count: number;
  category_breakdown: CategorySummary[];
  period: string;
}

export interface StatementRow {
  date: string;
  description: string;
  debit_amount: number;
  credit_amount?: number;
  reference_no?: string;
}

export interface ReconciliationItem {
  statement_row: StatementRow;
  matched_expense_id?: string;
  matched_merchant?: string;
  match_status: "EXACT_MATCH" | "UNMATCHED";
  suggested_category: string;
}

export interface ReconciliationResult {
  total_statement_items: number;
  matched_count: number;
  unmatched_count: number;
  items: ReconciliationItem[];
}

const API_BASE_URL = process.env.NEXT_PUBLIC_API_URL || "/api/v1";

export async function fetchExpenses(category?: string, source?: string): Promise<Expense[]> {
  try {
    let url = `${API_BASE_URL}/expenses/`;
    const params = new URLSearchParams();
    if (category) params.append("category", category);
    if (source) params.append("source", source);
    if (params.toString()) url += `?${params.toString()}`;

    const res = await fetch(url, { cache: "no-store" });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error("Failed to fetch expenses:", err);
    return [];
  }
}

export async function fetchAnalyticsSummary(days: number = 30): Promise<AnalyticsSummary | null> {
  try {
    const res = await fetch(`${API_BASE_URL}/analytics/summary?days=${days}`, { cache: "no-store" });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error("Failed to fetch analytics summary:", err);
    return null;
  }
}

export async function createExpense(data: Omit<Expense, "id">): Promise<Expense | null> {
  try {
    const res = await fetch(`${API_BASE_URL}/expenses/`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error("Failed to create expense:", err);
    return null;
  }
}

export async function updateExpense(id: string, data: Partial<Expense>): Promise<Expense | null> {
  try {
    const res = await fetch(`${API_BASE_URL}/expenses/${id}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(data),
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error(`Failed to update expense ${id}:`, err);
    return null;
  }
}

export async function deleteExpense(id: string): Promise<boolean> {
  try {
    const res = await fetch(`${API_BASE_URL}/expenses/${id}`, { method: "DELETE" });
    return res.ok;
  } catch (err) {
    console.error(`Failed to delete expense ${id}:`, err);
    return false;
  }
}

export async function uploadBankStatement(statementText: string): Promise<ReconciliationResult | null> {
  try {
    const res = await fetch(`${API_BASE_URL}/reconciliation/upload`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ statement_text: statementText }),
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error("Failed to upload bank statement:", err);
    return null;
  }
}

export async function confirmMissingTransactions(items: ReconciliationItem[]): Promise<Expense[]> {
  try {
    const res = await fetch(`${API_BASE_URL}/reconciliation/confirm`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ items_to_create: items }),
    });
    if (!res.ok) throw new Error(`HTTP error! status: ${res.status}`);
    return await res.json();
  } catch (err) {
    console.error("Failed to confirm missing transactions:", err);
    return [];
  }
}
