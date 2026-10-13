"use client";

import { useEffect, useState } from "react";
import { fetchAnalyticsSummary, AnalyticsSummary } from "@/lib/api";
import { PieChart, BarChart3, ShieldCheck } from "lucide-react";

export default function AnalyticsPage() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [days, setDays] = useState(30);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    setLoading(true);
    fetchAnalyticsSummary(days).then((data) => {
      setSummary(data);
      setLoading(false);
    });
  }, [days]);

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-white tracking-tight">Spending Analytics</h1>
          <p className="text-slate-400 text-sm mt-1">Deep analytics across historical transaction records.</p>
        </div>

        <div className="flex items-center gap-2 bg-slate-800 p-1 rounded-xl border border-slate-700">
          {[7, 30, 90].map((d) => (
            <button
              key={d}
              onClick={() => setDays(d)}
              className={`px-3 py-1.5 text-xs font-semibold rounded-lg transition-colors ${
                days === d ? "bg-indigo-600 text-white" : "text-slate-400 hover:text-white"
              }`}
            >
              {d} Days
            </button>
          ))}
        </div>
      </div>

      {loading ? (
        <div className="text-center py-12 text-slate-400 animate-pulse">Calculating analytics...</div>
      ) : (
        <div className="grid grid-cols-1 lg:grid-cols-2 gap-8">
          <div className="glass-panel p-6 border border-slate-700/60 space-y-6">
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <PieChart className="w-5 h-5 text-indigo-400" /> Category Breakdown
            </h2>

            <div className="space-y-4">
              {summary?.category_breakdown?.map((item) => {
                const percentage = summary.total_spent > 0
                  ? Math.round((item.total_amount / summary.total_spent) * 100)
                  : 0;

                return (
                  <div key={item.category} className="space-y-1.5">
                    <div className="flex justify-between text-sm">
                      <span className="font-semibold text-white">{item.category}</span>
                      <span className="text-slate-400">
                        ₹{item.total_amount.toLocaleString("en-IN")} ({percentage}%)
                      </span>
                    </div>
                    <div className="w-full bg-slate-800 rounded-full h-2.5 overflow-hidden">
                      <div
                        className="bg-gradient-to-r from-indigo-500 to-purple-500 h-2.5 rounded-full"
                        style={{ width: `${percentage}%` }}
                      />
                    </div>
                  </div>
                );
              })}
            </div>
          </div>

          <div className="glass-panel p-6 border border-slate-700/60 space-y-6">
            <h2 className="text-xl font-bold text-white flex items-center gap-2">
              <BarChart3 className="w-5 h-5 text-emerald-400" /> Key Metrics
            </h2>

            <div className="space-y-4 divide-y divide-slate-800">
              <div className="pt-2 flex justify-between items-center">
                <span className="text-slate-400 text-sm">Total Cycle Volume</span>
                <span className="font-bold text-white text-lg">₹{summary?.total_spent?.toLocaleString("en-IN")}</span>
              </div>

              <div className="pt-4 flex justify-between items-center">
                <span className="text-slate-400 text-sm">Total Logged Transactions</span>
                <span className="font-bold text-white text-lg">{summary?.transaction_count}</span>
              </div>

              <div className="pt-4 flex justify-between items-center">
                <span className="text-slate-400 text-sm">Average Ticket Size</span>
                <span className="font-bold text-white text-lg">
                  ₹{summary && summary.transaction_count > 0
                    ? Math.round(summary.total_spent / summary.transaction_count).toLocaleString("en-IN")
                    : 0}
                </span>
              </div>

              <div className="pt-4 flex justify-between items-center">
                <span className="text-slate-400 text-sm">Data Pipeline Status</span>
                <span className="text-xs font-semibold text-emerald-400 bg-emerald-500/10 px-2.5 py-1 rounded-full border border-emerald-500/20 flex items-center gap-1">
                  <ShieldCheck className="w-3.5 h-3.5" /> Healthy
                </span>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
