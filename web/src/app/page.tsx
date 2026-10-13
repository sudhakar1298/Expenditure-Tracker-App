"use client";

import { useEffect, useState } from "react";
import Link from "next/link";
import { fetchAnalyticsSummary, fetchExpenses, AnalyticsSummary, Expense } from "@/lib/api";
import { Wallet, TrendingUp, CreditCard, ArrowRight, Activity, Plus } from "lucide-react";
import AddExpenseModal from "@/components/AddExpenseModal";

export default function Home() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [recentExpenses, setRecentExpenses] = useState<Expense[]>([]);
  const [isModalOpen, setIsModalOpen] = useState(false);
  const [loading, setLoading] = useState(true);

  const loadData = async () => {
    setLoading(true);
    const [summaryData, expensesData] = await Promise.all([
      fetchAnalyticsSummary(30),
      fetchExpenses(),
    ]);
    setSummary(summaryData);
    setRecentExpenses(expensesData.slice(0, 5));
    setLoading(false);
  };

  useEffect(() => {
    loadData();
    const timer = window.setInterval(loadData, 10000);
    window.addEventListener("focus", loadData);
    return () => { window.clearInterval(timer); window.removeEventListener("focus", loadData); };
  }, []);

  return (
    <div className="space-y-8">
      {/* Top Banner */}
      <div className="glass-panel p-8 relative overflow-hidden flex flex-col md:flex-row items-start md:items-center justify-between gap-6 border border-slate-700/60 shadow-xl">
        <div className="space-y-2 z-10 max-w-2xl">
          <span className="bg-indigo-500/20 text-indigo-300 border border-indigo-500/30 text-xs font-semibold px-3 py-1 rounded-full uppercase tracking-wider">
            Live Synchronization
          </span>
          <h1 className="text-3xl sm:text-4xl font-extrabold text-white tracking-tight">
            Financial Dashboard
          </h1>
          <p className="text-slate-400 text-sm sm:text-base">
            Automatically aggregating spend data from Android SMS alerts, Google Pay notifications, and direct backend APIs.
          </p>
        </div>

        <button
          onClick={() => setIsModalOpen(true)}
          className="glass-button text-white font-semibold px-5 py-3 rounded-xl flex items-center gap-2 shadow-lg hover:scale-105 transition-transform"
        >
          <Plus className="w-5 h-5" /> Add Expense
        </button>
      </div>

      {/* Metrics Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
        <div className="glass-panel p-6 border border-slate-700/60 flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Monthly Spending</p>
            <h3 className="text-3xl font-extrabold text-white mt-2">
              ₹{summary?.total_spent?.toLocaleString("en-IN") || "0"}
            </h3>
            <p className="text-xs text-indigo-400 mt-1 flex items-center gap-1">
              <TrendingUp className="w-3.5 h-3.5" /> Last 30 days active cycle
            </p>
          </div>
          <div className="p-4 bg-indigo-600/20 text-indigo-400 rounded-2xl border border-indigo-500/30">
            <Wallet className="w-7 h-7" />
          </div>
        </div>

        <div className="glass-panel p-6 border border-slate-700/60 flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Transaction Count</p>
            <h3 className="text-3xl font-extrabold text-white mt-2">
              {summary?.transaction_count || 0}
            </h3>
            <p className="text-xs text-emerald-400 mt-1 flex items-center gap-1">
              <Activity className="w-3.5 h-3.5" /> Logged & Processed
            </p>
          </div>
          <div className="p-4 bg-emerald-600/20 text-emerald-400 rounded-2xl border border-emerald-500/30">
            <CreditCard className="w-7 h-7" />
          </div>
        </div>

        <div className="glass-panel p-6 border border-slate-700/60 flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-slate-400 uppercase tracking-wider">Top Category</p>
            <h3 className="text-2xl font-bold text-white mt-2">
              {summary?.category_breakdown?.[0]?.category || "N/A"}
            </h3>
            <p className="text-xs text-amber-400 mt-1">
              ₹{summary?.category_breakdown?.[0]?.total_amount?.toLocaleString("en-IN") || "0"}
            </p>
          </div>
          <div className="p-4 bg-amber-600/20 text-amber-400 rounded-2xl border border-amber-500/30">
            <TrendingUp className="w-7 h-7" />
          </div>
        </div>
      </div>

      {/* Recent Activity Table */}
      <div className="glass-panel p-6 border border-slate-700/60">
        <div className="flex items-center justify-between mb-6">
          <h2 className="text-xl font-bold text-white">Recent Transactions</h2>
          <Link
            href="/transactions"
            className="text-xs font-semibold text-indigo-400 hover:text-indigo-300 flex items-center gap-1"
          >
            View All <ArrowRight className="w-4 h-4" />
          </Link>
        </div>

        {loading ? (
          <div className="text-center py-8 text-slate-400 animate-pulse">Loading financial activity...</div>
        ) : recentExpenses.length === 0 ? (
          <div className="text-center py-8 text-slate-400">No expenses recorded yet.</div>
        ) : (
          <div className="divide-y divide-slate-800">
            {recentExpenses.map((exp) => (
              <div key={exp.id} className="py-4 flex items-center justify-between hover:bg-slate-800/40 px-3 rounded-xl transition-colors">
                <div>
                  <p className="font-semibold text-white">{exp.merchant}</p>
                  <p className="text-xs text-slate-400">
                    {exp.category} • <span className="text-indigo-400">{exp.source}</span>
                  </p>
                </div>
                <div className="text-right">
                  <p className="font-bold text-white">₹{exp.amount.toLocaleString("en-IN")}</p>
                  <p className="text-xs text-slate-500">
                    {new Date(exp.transaction_date).toLocaleDateString()}
                  </p>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      <AddExpenseModal
        isOpen={isModalOpen}
        onClose={() => setIsModalOpen(false)}
        onSuccess={loadData}
      />
    </div>
  );
}
