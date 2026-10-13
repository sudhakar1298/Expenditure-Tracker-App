"use client";

import Link from "next/link";
import { usePathname } from "next/navigation";
import { Shield, LayoutDashboard, ReceiptText, PieChart, FileCheck, Wallet } from "lucide-react";
import ThemeToggle from "@/components/ThemeToggle";

export default function Navbar() {
  const pathname = usePathname();

  const navItems = [
    { name: "Overview", href: "/", icon: Wallet },
    { name: "Dashboard", href: "/dashboard", icon: LayoutDashboard },
    { name: "Transactions", href: "/transactions", icon: ReceiptText },
    { name: "Analytics", href: "/analytics", icon: PieChart },
    { name: "Reconcile", href: "/import-statement", icon: FileCheck },
  ];

  return (
    <nav className="glass-panel sticky top-4 z-50 mx-auto max-w-7xl px-6 py-3 mb-8 flex items-center justify-between">
      <Link href="/" className="flex items-center gap-3">
        <div className="w-9 h-9 rounded-xl bg-[#4D6B3C]/20 border border-[#4D6B3C]/40 flex items-center justify-center text-[#7E9A68] shadow-sm">
          <Shield className="w-5 h-5 text-[var(--primary-accent)]" />
        </div>
        <span className="font-bold text-lg tracking-tight text-[var(--text-main)]">VaultGlass</span>
      </Link>

      <div className="hidden sm:flex items-center gap-1 bg-[var(--input-bg)] p-1.5 rounded-2xl border border-[var(--card-border)]">
        {navItems.map((item) => {
          const Icon = item.icon;
          const isActive = pathname === item.href;
          return (
            <Link
              key={item.href}
              href={item.href}
              className={`flex items-center gap-2 px-3.5 py-1.5 rounded-xl font-medium text-xs sm:text-sm transition-all ${
                isActive
                  ? "bg-[var(--primary-accent)] text-[var(--primary-btn-text)] shadow-md"
                  : "text-[var(--text-muted)] hover:text-[var(--text-main)]"
              }`}
            >
              <Icon className="w-4 h-4" />
              {item.name}
            </Link>
          );
        })}
      </div>

      <div className="flex items-center gap-3">
        <ThemeToggle />
        <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-[#7E9A68] to-[#4D6B3C] flex items-center justify-center font-bold text-white text-xs border border-white/20 shadow-sm">
          SU
        </div>
      </div>
    </nav>
  );
}
