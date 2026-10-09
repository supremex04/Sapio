import { AlertCircle, CheckCircle2, Info } from "lucide-react";
import type { ReactNode } from "react";

type AlertTone = "error" | "info" | "success";
const ICONS = { error: AlertCircle, info: Info, success: CheckCircle2 } as const;

export function Alert({ tone = "error", children, action }: { tone?: AlertTone; children: ReactNode; action?: ReactNode }) {
  const Icon = ICONS[tone];
  return (
    <div className={`alert alert-${tone}`} role={tone === "error" ? "alert" : "status"}>
      <Icon size={18} aria-hidden />
      <div style={{ flex: 1 }}>{children}</div>
      {action}
    </div>
  );
}
