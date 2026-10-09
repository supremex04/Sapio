import { masteryLabel } from "../path";
import "./study-components.css";

/** Five-segment mastery meter; empty until the learner has practised the topic. */
export function MasteryMeter({ mastery, showLabel = true }: { mastery: number | null; showLabel?: boolean }) {
  const filled = mastery == null ? 0 : Math.round(mastery * 5);
  const tone = mastery == null ? "none" : mastery >= 0.8 ? "high" : mastery >= 0.5 ? "mid" : "low";
  return (
    <span className="mastery" title={`Mastery: ${masteryLabel(mastery)}`}>
      <span className={`mastery-bars mastery-${tone}`} role="img" aria-label={`Mastery: ${masteryLabel(mastery)}${mastery != null ? ` (${Math.round(mastery * 100)}%)` : ""}`}>
        {[0, 1, 2, 3, 4].map((i) => (
          <span key={i} className={i < filled ? "on" : undefined} />
        ))}
      </span>
      {showLabel && <span className="xs muted">{masteryLabel(mastery)}</span>}
    </span>
  );
}
