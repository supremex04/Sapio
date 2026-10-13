import type { ReactNode } from "react";
import { Sage, type SageMood } from "../illustrations/Sage";

/** Empty / zero states feature Sapi by default: a friendly face instead of a grey icon. */
export function EmptyState({ icon, mood = "happy", title, children, action }: { icon?: ReactNode; mood?: SageMood; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-icon" aria-hidden>
        {icon ?? <Sage mood={mood} size={88} />}
      </div>
      <h3>{title}</h3>
      {children && <p className="small">{children}</p>}
      {action}
    </div>
  );
}
