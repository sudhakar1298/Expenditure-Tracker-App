"use client";

import { useEffect, useState } from "react";
import { fetchAnalyticsSummary, AnalyticsSummary } from "@/lib/api";
import { Utensils, Car, ShoppingBag, FileText, Film, Layers } from "lucide-react";

export default function Dashboard() {
  const [summary, setSummary] = useState<AnalyticsSummary | null>(null);
  const [loading, setLoading] = useState(true);

  useEffect(() => {
    fetchAnalyticsSummary(30).then((data) => {
      setSummary(data);
      setLoading(false);
    });
  }, []);

  const getCategoryIcon = (category: string) => {
    switch (category.toLowerCase()) {
      case "food":
        return Utensils;
      case "travel":
        return Car;
      case "shopping":
        return ShoppingBag;
      case "bills":
        return FileText;
      case "entertainment":
        return Film;
      default:
        return Layers;
    }
  };

  return (
    <div className="space-y-8">
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-3xl font-bold text-white tracking-tight">Category Breakdown</h1>
          <p className="text-slate-400 text-sm mt-1">Aggregated expenditure distribution for the last 30 days.</p>
        </div>
      </div>

      {loading ? (
        <div className="text-center py-12 text-slate-400 animate-pulse">Loading category metrics...</div>
      ) : (
        <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-6">
          {summary?.category_breakdown?.map((cat) => {
            const Icon = getCategoryIcon(cat.category);
            const percentage = summary.total_spent > 0
              ? Math.round((cat.total_amount / summary.total_spent) * 100)
              : 0;

            return (
              <div key={cat.category} className="glass-panel p-6 border border-slate-700/60 flex flex-col justify-between space-y-4">
                <div className="flex items-center justify-between">
                  <div className="flex items-center gap-3">
                    <div className="p-3 bg-indigo-600/20 text-indigo-400 rounded-xl border border-indigo-500/30">
                      <Icon className="w-6 h-6" />
                    </div>
                    <div>
                      <h3 className="font-bold text-white text-lg">{cat.category}</h3>
                      <p className="text-xs text-slate-400">{cat.count} transactions</p>
                    </div>
                  </div>
                  <span className="text-xs font-semibold bg-slate-800 text-indigo-300 px-2.5 py-1 rounded-full border border-slate-700">
                    {percentage}%
                  </span>
                </div>

                <div className="space-y-2">
                  <div className="flex justify-between items-end">
                    <span className="text-xs text-slate-400">Spent</span>
                    <span className="text-xl font-extrabold text-white">₹{cat.total_amount.toLocaleString("en-IN")}</span>
                  </div>

                  <div className="w-full bg-slate-800 rounded-full h-2 overflow-hidden">
                    <div
                      className="bg-indigo-500 h-2 rounded-full transition-all duration-500"
                      style={{ width: `${percentage}%` }}
                    />
                  </div>
                </div>
              </div>
            );
          })}
        </div>
      )}
    </div>
  );
}
