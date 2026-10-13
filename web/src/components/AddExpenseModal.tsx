"use client";

import { useState } from "react";
import { Plus, X } from "lucide-react";
import { createExpense } from "@/lib/api";

interface AddExpenseModalProps {
  isOpen: boolean;
  onClose: () => void;
  onSuccess: () => void;
}

export default function AddExpenseModal({ isOpen, onClose, onSuccess }: AddExpenseModalProps) {
  const [amount, setAmount] = useState("");
  const [merchant, setMerchant] = useState("");
  const [reason, setReason] = useState("");
  const [error, setError] = useState("");
  const [transactionId, setTransactionId] = useState<string | null>(null);
  const [category, setCategory] = useState("Food");
  const [source, setSource] = useState("Manual");
  const [loading, setLoading] = useState(false);

  if (!isOpen) return null;

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault();
    if (!amount || !merchant) return;

    setLoading(true);
    setError("");
    const id = transactionId || "web-" + Array.from(crypto.getRandomValues(new Uint8Array(16)),
      (byte) => byte.toString(16).padStart(2, "0")).join("");
    setTransactionId(id);
    const newExpense = await createExpense({
      amount: parseFloat(amount),
      merchant,
      reason,
      transaction_id: id,
      category,
      source,
      currency: "INR",
      is_pending: false,
      transaction_date: new Date().toISOString(),
    });

    setLoading(false);
    if (newExpense) {
      setAmount("");
      setMerchant("");
      setReason("");
      setTransactionId(null);
      onSuccess();
      onClose();
    } else {
      setError("Save failed. Please retry; your entry is still here.");
    }
  };

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/60 backdrop-blur-sm p-4">
      <div className="glass-panel w-full max-w-md p-6 relative border border-[var(--card-border)] shadow-2xl">
        <button
          onClick={onClose}
          className="absolute top-4 right-4 text-[var(--text-muted)] hover:text-[var(--text-main)] p-1 rounded-lg transition-colors"
        >
          <X className="w-5 h-5" />
        </button>

        <h2 className="text-xl font-bold text-[var(--text-main)] mb-6 flex items-center gap-2">
          <Plus className="w-5 h-5 text-[var(--primary-accent)]" /> Add New Expense
        </h2>

        <form onSubmit={handleSubmit} className="space-y-4">
          <div>
            <label className="block text-xs font-semibold text-[var(--text-muted)] uppercase mb-1">Amount (₹)</label>
            <input
              type="number"
              step="0.01"
              value={amount}
              onChange={(e) => setAmount(e.target.value)}
              placeholder="e.g. 450"
              required
              className="w-full bg-[var(--input-bg)] border border-[var(--input-border)] rounded-xl px-4 py-2.5 text-[var(--text-main)] placeholder-slate-400 focus:outline-none focus:border-[var(--primary-accent)]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--text-muted)] uppercase mb-1">Merchant / Recipient</label>
            <input
              type="text"
              value={merchant}
              onChange={(e) => setMerchant(e.target.value)}
              placeholder="e.g. Swiggy, Amazon"
              required
              className="w-full bg-[var(--input-bg)] border border-[var(--input-border)] rounded-xl px-4 py-2.5 text-[var(--text-main)] placeholder-slate-400 focus:outline-none focus:border-[var(--primary-accent)]"
            />
          </div>

          <div>
            <label className="block text-xs font-semibold text-[var(--text-muted)] uppercase mb-1">Reason</label>
            <input value={reason} onChange={(e) => setReason(e.target.value)}
              className="w-full bg-[var(--input-bg)] border border-[var(--input-border)] rounded-xl px-4 py-2.5 text-[var(--text-main)]" />
          </div>
          {error && <p role="alert" className="text-red-400">{error}</p>}
          <p className="text-xs text-[var(--text-muted)]">Saving queues this expense for Databricks. Check the sync status after saving.</p>
          <div className="grid grid-cols-2 gap-4">
            <div>
              <label className="block text-xs font-semibold text-[var(--text-muted)] uppercase mb-1">Category</label>
              <select
                value={category}
                onChange={(e) => setCategory(e.target.value)}
                className="w-full bg-[var(--input-bg)] border border-[var(--input-border)] rounded-xl px-3 py-2.5 text-[var(--text-main)] focus:outline-none focus:border-[var(--primary-accent)]"
              >
                <option value="Food">Food</option>
                <option value="Travel">Travel</option>
                <option value="Shopping">Shopping</option>
                <option value="Bills">Bills</option>
                <option value="Entertainment">Entertainment</option>
                <option value="Others">Others</option>
              </select>
            </div>

            <div>
              <label className="block text-xs font-semibold text-[var(--text-muted)] uppercase mb-1">Source</label>
              <select
                value={source}
                onChange={(e) => setSource(e.target.value)}
                className="w-full bg-[var(--input-bg)] border border-[var(--input-border)] rounded-xl px-3 py-2.5 text-[var(--text-main)] focus:outline-none focus:border-[var(--primary-accent)]"
              >
                <option value="Manual">Manual</option>
                <option value="GPay">GPay</option>
                <option value="SMS">SMS</option>
                <option value="Statement">Statement</option>
              </select>
            </div>
          </div>

          <button
            type="submit"
            disabled={loading}
            className="w-full glass-button font-semibold py-3 rounded-xl mt-4 flex items-center justify-center gap-2 shadow-lg"
          >
            {loading ? "Saving..." : "Save Expense"}
          </button>
        </form>
      </div>
    </div>
  );
}
