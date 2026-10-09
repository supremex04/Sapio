import type { HTMLAttributes, ReactNode } from "react";
import { cx } from "./cx";

export type CardTone = "blue" | "green" | "amber" | "purple" | "teal" | "red";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  tight?: boolean;
  interactive?: boolean;
  /** Tinted card in one of the palette hues (see design-system.md: one hue per job). */
  tone?: CardTone;
}

export function Card({ tight, interactive, tone, className, ...rest }: CardProps) {
  return <div className={cx("card", tight && "card-tight", interactive && "card-interactive", tone && `card-${tone}`, className)} {...rest} />;
}

export function CardHeader({ icon, title, action }: { icon?: ReactNode; title: ReactNode; action?: ReactNode }) {
  return (
    <div className="card-header">
      <h2 className="card-title">
        {icon}
        {title}
      </h2>
      {action}
    </div>
  );
}
