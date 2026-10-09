import type { ReactNode } from "react";

export function Alert({ tone = "error", children }: { tone?: "error" | "info"; children: ReactNode }) {
  return (
    <div className={`alert alert-${tone}`} role={tone === "error" ? "alert" : "status"}>
      {children}
    </div>
  );
}
