"use client";

import { useState } from "react";
import { uploadBankStatement, confirmMissingTransactions, ReconciliationResult, ReconciliationItem } from "@/lib/api";
import { Upload, CheckCircle2, AlertCircle, FileCheck, ArrowRight } from "lucide-react";

export default function ImportStatementPage() {
  const [statementText, setStatementText] = useState(
    "Date,Description,Debit,Reference\n2026-10-01,UPI-SWIGGY-REST,450.00,REF12345\n2026-10-02,ACT FIBERNET BILL,999.00,REF67890"
  );
  const [reconciliation, setReconciliation] = useState<ReconciliationResult | null>(null);
  const [loading, setLoading] = useState(false);
  const [saving, setSaving] = useState(false);
  const [savedSuccess, setSavedSuccess] = useState(false);

  const handleUpload = async () => {
    if (!statementText.trim()) return;
    setLoading(true);
    setSavedSuccess(false);
    const result = await uploadBankStatement(statementText);
    setReconciliation(result);
    setLoading(false);
  };

  const handleConfirmMissing = async () => {
    if (!reconciliation) return;
    const unmatched = reconciliation.items.filter((it) => it.match_status === "UNMATCHED");
    if (unmatched.length === 0) return;

    setSaving(true);
    const created = await confirmMissingTransactions(unmatched);
    setSaving(false);
    if (created && created.length > 0) {
      setSavedSuccess(true);
      // Mark confirmed items as matched locally
      setReconciliation({
        ...reconciliation,
        matched_count: reconciliation.matched_count + created.length,
        unmatched_count: 0,
        items: reconciliation.items.map((it) => ({
          ...it,
          match_status: "EXACT_MATCH",
        })),
      });
    }
  };

  const updateSuggestedCategory = (index: number, newCategory: string) => {
    if (!reconciliation) return;
    const newItems = [...reconciliation.items];
    newItems[index].suggested_category = newCategory;
    setReconciliation({ ...reconciliation, items: newItems });
  };

  return (
    <div className="space-y-8">
      <div>
        <h1 className="text-3xl font-bold text-white tracking-tight flex items-center gap-2">
          <FileCheck className="w-8 h-8 text-indigo-400" /> Bank Statement Reconciliation
        </h1>
        <p className="text-slate-400 text-sm mt-1">
          Upload bank statements to audit against logged expenses and catch unrecorded transactions.
        </p>
      </div>

      {/* Input Section */}
      <div className="glass-panel p-6 border border-slate-700/60 space-y-4">
        <label className="block text-xs font-semibold text-slate-400 uppercase tracking-wider">
          Paste Bank Statement CSV Content
        </label>
        <textarea
          rows={6}
          value={statementText}
          onChange={(e) => setStatementText(e.target.value)}
          placeholder="Paste CSV with Date, Description, Debit columns..."
          className="w-full bg-slate-900 border border-slate-700 rounded-xl p-4 font-mono text-xs text-slate-200 focus:outline-none focus:border-indigo-500"
        />

        <button
          onClick={handleUpload}
          disabled={loading}
          className="glass-button text-white font-semibold px-6 py-3 rounded-xl flex items-center gap-2 shadow-lg text-sm"
        >
          <Upload className="w-4 h-4" /> {loading ? "Analyzing..." : "Reconcile Statement"}
        </button>
      </div>

      {/* Results Section */}
      {reconciliation && (
        <div className="space-y-6">
          {savedSuccess && (
            <div className="p-4 bg-emerald-500/20 border border-emerald-500/40 rounded-xl text-emerald-300 text-sm flex items-center gap-2">
              <CheckCircle2 className="w-5 h-5 text-emerald-400" /> Missing statement transactions have been successfully categorized & saved!
            </div>
          )}

          {/* Audit Metrics */}
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-6">
            <div className="glass-panel p-5 border border-slate-700/60">
              <p className="text-xs text-slate-400 font-semibold uppercase">Total Statement Items</p>
              <p className="text-2xl font-bold text-white mt-1">{reconciliation.total_statement_items}</p>
            </div>

            <div className="glass-panel p-5 border border-slate-700/60">
              <p className="text-xs text-emerald-400 font-semibold uppercase flex items-center gap-1">
                <CheckCircle2 className="w-4 h-4" /> Matched Expenses
              </p>
              <p className="text-2xl font-bold text-emerald-400 mt-1">{reconciliation.matched_count}</p>
            </div>

            <div className="glass-panel p-5 border border-slate-700/60">
              <p className="text-xs text-amber-400 font-semibold uppercase flex items-center gap-1">
                <AlertCircle className="w-4 h-4" /> Unmatched / Missing
              </p>
              <p className="text-2xl font-bold text-amber-400 mt-1">{reconciliation.unmatched_count}</p>
            </div>
          </div>

          {/* Detailed Item List */}
          <div className="glass-panel overflow-hidden border border-slate-700/60">
            <div className="p-4 border-b border-slate-700/60 flex items-center justify-between">
              <h2 className="font-bold text-white text-lg">Reconciliation Table</h2>
              {reconciliation.unmatched_count > 0 && (
                <button
                  onClick={handleConfirmMissing}
                  disabled={saving}
                  className="bg-amber-600 hover:bg-amber-500 text-white font-semibold text-xs px-4 py-2 rounded-xl transition-all flex items-center gap-1 shadow-lg shadow-amber-600/30"
                >
                  {saving ? "Saving..." : `Import ${reconciliation.unmatched_count} Missing Expenses`}
                  <ArrowRight className="w-4 h-4" />
                </button>
              )}
            </div>

            <div className="overflow-x-auto">
              <table className="w-full text-left text-sm text-slate-300">
                <thead className="bg-slate-800/80 uppercase text-xs text-slate-400 border-b border-slate-700">
                  <tr>
                    <th className="py-3 px-4">Statement Date</th>
                    <th className="py-3 px-4">Description</th>
                    <th className="py-3 px-4">Match Status</th>
                    <th className="py-3 px-4">Category</th>
                    <th className="py-3 px-4 text-right">Debit Amount</th>
                  </tr>
                </thead>
                <tbody className="divide-y divide-slate-800">
                  {reconciliation.items.map((item, idx) => (
                    <tr key={idx} className="hover:bg-slate-800/40 transition-colors">
                      <td className="py-3.5 px-4 text-xs text-slate-400">
                        {new Date(item.statement_row.date).toLocaleDateString()}
                      </td>
                      <td className="py-3.5 px-4 font-semibold text-white">
                        {item.statement_row.description}
                      </td>
                      <td className="py-3.5 px-4">
                        {item.match_status === "EXACT_MATCH" ? (
                          <span className="bg-emerald-500/10 text-emerald-400 border border-emerald-500/20 px-2.5 py-0.5 rounded-full text-xs font-semibold flex items-center gap-1 w-fit">
                            <CheckCircle2 className="w-3.5 h-3.5" /> Matched ({item.matched_merchant})
                          </span>
                        ) : (
                          <span className="bg-amber-500/10 text-amber-400 border border-amber-500/20 px-2.5 py-0.5 rounded-full text-xs font-semibold flex items-center gap-1 w-fit">
                            <AlertCircle className="w-3.5 h-3.5" /> Missing
                          </span>
                        )}
                      </td>
                      <td className="py-3.5 px-4">
                        {item.match_status === "UNMATCHED" ? (
                          <select
                            value={item.suggested_category}
                            onChange={(e) => updateSuggestedCategory(idx, e.target.value)}
                            className="bg-slate-900 border border-slate-700 rounded-lg px-2 py-1 text-xs text-white focus:outline-none focus:border-indigo-500"
                          >
                            <option value="Food">Food</option>
                            <option value="Travel">Travel</option>
                            <option value="Shopping">Shopping</option>
                            <option value="Bills">Bills</option>
                            <option value="Entertainment">Entertainment</option>
                            <option value="Others">Others</option>
                          </select>
                        ) : (
                          <span className="text-xs text-slate-400">{item.suggested_category}</span>
                        )}
                      </td>
                      <td className="py-3.5 px-4 text-right font-bold text-white">
                        ₹{item.statement_row.debit_amount.toLocaleString("en-IN")}
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
