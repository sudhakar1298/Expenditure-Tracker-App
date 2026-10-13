"use client";
import { useEffect, useState } from "react";

export default function DatabricksSyncStatus() {
  const [message, setMessage] = useState("Checking Databricks delivery…");
  useEffect(() => {
    let active = true;
    async function refresh() {
      try {
        const response = await fetch("/api/v1/databricks/status", { cache: "no-store" });
        if (!response.ok) throw new Error();
        const status = await response.json();
        if (active) setMessage(!status.enabled ? "Databricks sync is disabled" :
          status.pending > 0 ? `Databricks: ${status.pending} pending uploads${status.failed_attempts ? ` (${status.failed_attempts} awaiting retry)` : ""}` :
          "Databricks: no pending uploads");
      } catch {
        if (active) setMessage("Databricks delivery status unavailable");
      }
    }
    refresh();
    const timer = window.setInterval(refresh, 3000);
    return () => { active = false; window.clearInterval(timer); };
  }, []);
  return <div role="status" className="max-w-7xl mx-auto mb-4 text-sm text-slate-300">{message}</div>;
}
