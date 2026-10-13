"use client";

import { useEffect, useState } from "react";
import { fetchExpenses, deleteExpense, Expense } from "@/lib/api";
import { Search, Plus, Trash2, Filter } from "lucide-react";
import AddExpenseModal from "@/components/AddExpenseModal";

export default function TransactionsPage() {
  const [expenses, setExpenses] = useState<Expense[]>([]);
  const [search, setSearch] = useState("");
  const [selectedCategory, setSelectedCategory] = useState<string>("All");
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadExpenses = async () => {
    setLoading(true);
    const cat = selectedCategory === "All" ? undefined : selectedCategory;
    const data = await fetchExpenses(cat);
    setExpenses(data);
    setLoading(false);
  };

  useEffect(() => {
    loadExpenses();
    const timer = window.setInterval(loadExpenses, 10000);
    window.addEventListener("focus", loadExpenses);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", loadExpenses); };
  }, [selectedCategory]);

  const handleDelete = async (id: string) => {
    if (confirm("Are you sure you want to delete this transaction?")) {
      const ok = await deleteExpense(id);
      if (ok) loadExpenses();
    }
  };

  const filteredExpenses = expenses.filter((exp) =>
    exp.merchant.toLowerCase().includes(search.toLowerCase()) ||
    (exp.reason || "").toLowerCase().includes(search.toLowerCase()) ||
    exp.category.toLowerCase().includes(search.toLowerCase())
  );

  return (
    <div className="space-y-6">
      <div className="flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
        <div>
          <h1 className="text-3xl font-bold text-white tracking-tight">Transactions</h1>
          <p className="text-slate-400 text-sm mt-1">Full audit log of synced expenses.</p>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="glass-button text-white font-semibold px-4 py-2.5 rounded-xl flex items-center gap-2 text-sm"
        >
          <Plus className="w-4 h-4" /> Add Expense
        </button>
      </div>

      {/* Filter and Search */}
      <div className="glass-panel p-4 border border-slate-700/60 flex flex-col sm:flex-row items-center justify-between gap-4">
        <div className="relative w-full sm:w-80">
          <Search className="w-4 h-4 absolute left-3 top-3 text-slate-400" />
          <input
            type="text"
            placeholder="Search merchant or category..."
            value={search}
            onChange={(e) => setSearch(e.target.value)}
            className="w-full bg-slate-900 border border-slate-700 rounded-xl pl-9 pr-4 py-2 text-sm text-white placeholder-slate-500 focus:outline-none focus:border-indigo-500"
          />
        </div>

        <div className="flex items-center gap-2 w-full sm:w-auto">
          <Filter className="w-4 h-4 text-slate-400" />
          <select
            value={selectedCategory}
            onChange={(e) => setSelectedCategory(e.target.value)}
            className="bg-slate-900 border border-slate-700 rounded-xl px-3 py-2 text-sm text-white focus:outline-none focus:border-indigo-500 w-full sm:w-48"
          >
            <option value="All">All Categories</option>
            <option value="Food">Food</option>
            <option value="Travel">Travel</option>
            <option value="Shopping">Shopping</option>
            <option value="Bills">Bills</option>
            <option value="Entertainment">Entertainment</option>
            <option value="Others">Others</option>
          </select>
        </div>
      </div>

      {/* Transactions Table */}
      <div className="glass-panel overflow-hidden border border-slate-700/60">
        {loading ? (
          <div className="p-8 text-center text-slate-400 animate-pulse">Loading transactions...</div>
        ) : filteredExpenses.length === 0 ? (
          <div className="p-8 text-center text-slate-400">No matching transactions found.</div>
        ) : (
          <div className="overflow-x-auto">
            <table className="w-full text-left text-sm text-slate-300">
              <thead className="bg-slate-800/80 uppercase text-xs text-slate-400 border-b border-slate-700">
                <tr>
                  <th className="py-3 px-4">Merchant</th>
                  <th className="py-3 px-4">Category</th>
                  <th className="py-3 px-4">Source</th>
                  <th className="py-3 px-4">Date</th>
                  <th className="py-3 px-4 text-right">Amount</th>
                  <th className="py-3 px-4 text-center">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-slate-800">
                {filteredExpenses.map((exp) => (
                  <tr key={exp.id} className="hover:bg-slate-800/40 transition-colors">
                    <td className="py-3.5 px-4 font-semibold text-white">
                      {exp.merchant}
                      {exp.reason && <div className="text-xs font-normal text-slate-400 mt-1">{exp.reason}</div>}
                    </td>
                    <td className="py-3.5 px-4">
                      <span className="bg-indigo-500/10 text-indigo-300 border border-indigo-500/20 px-2.5 py-0.5 rounded-full text-xs font-medium">
                        {exp.category}
                      </span>
                    </td>
                    <td className="py-3.5 px-4 text-xs text-slate-400">{exp.source}</td>
                    <td className="py-3.5 px-4 text-xs text-slate-400">
                      {new Date(exp.transaction_date).toLocaleDateString()}
                    </td>
                    <td className="py-3.5 px-4 text-right font-bold text-white">
                      ₹{exp.amount.toLocaleString("en-IN")}
                    </td>
                    <td className="py-3.5 px-4 text-center">
                      <button
                        onClick={() => handleDelete(exp.id)}
                        className="text-slate-500 hover:text-red-400 p-1.5 rounded-lg transition-colors"
                        title="Delete Transaction"
                      >
                        <Trash2 className="w-4 h-4" />
                      </button>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>

      <AddExpenseModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={loadExpenses}
      />
    </div>
  );
}
