import type { Metadata } from "next";
import Navbar from "@/components/Navbar";
import DatabricksSyncStatus from "@/components/DatabricksSyncStatus";
import "./globals.css";

export const metadata: Metadata = {
  title: "GPay Expense Tracker | Next.js Dashboard",
  description: "Track and manage expenses automatically synced from Android SMS & GPay notifications.",
};

export default function RootLayout({
  children,
}: {
  children: React.ReactNode;
}) {
  return (
    <html lang="en">
      <body className="bg-slate-950 text-slate-100 min-h-screen p-4 sm:p-6 md:p-8">
        <Navbar />
        <DatabricksSyncStatus />
        <main className="max-w-7xl mx-auto">{children}</main>
      </body>
    </html>
  );
}
