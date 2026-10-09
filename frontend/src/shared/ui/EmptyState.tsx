import type { ReactNode } from "react";
import { Dia, type DiaMood } from "../illustrations/Dia";

/** Empty / zero states feature Dia by default: a friendly face instead of a grey icon. */
export function EmptyState({ icon, mood = "happy", title, children, action }: { icon?: ReactNode; mood?: DiaMood; title: string; children?: ReactNode; action?: ReactNode }) {
  return (
    <div className="empty">
      <div className="empty-icon" aria-hidden>
        {icon ?? <Dia mood={mood} size={88} />}
      </div>
      <h3>{title}</h3>
      {children && <p className="small">{children}</p>}
      {action}
    </div>
  );
}
