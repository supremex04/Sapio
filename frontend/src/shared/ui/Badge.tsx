import type { ReactNode } from "react";
import { cx } from "./cx";

export type Tone = "neutral" | "primary" | "accent" | "success" | "warning" | "danger" | "mastery" | "review";

export function Badge({ tone = "neutral", icon, children, className }: { tone?: Tone; icon?: ReactNode; children: ReactNode; className?: string }) {
  return (
    <span className={cx("badge", tone !== "neutral" && `badge-${tone}`, className)}>
      {icon}
      {children}
    </span>
  );
}
