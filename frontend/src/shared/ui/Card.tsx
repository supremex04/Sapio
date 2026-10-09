import type { HTMLAttributes, ReactNode } from "react";
import { cx } from "./cx";

interface CardProps extends HTMLAttributes<HTMLDivElement> {
  tight?: boolean;
  interactive?: boolean;
}

export function Card({ tight, interactive, className, ...rest }: CardProps) {
  return <div className={cx("card", tight && "card-tight", interactive && "card-interactive", className)} {...rest} />;
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
