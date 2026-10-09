import { cx } from "../../shared/ui/cx";
import { GOAL_OPTIONS, type GoalMinutes } from "./types";

const LABELS: Record<GoalMinutes, string> = { 5: "Casual", 10: "Steady", 15: "Focused", 20: "Serious", 30: "Intense" };

export function GoalPicker({ value, onChange }: { value: number | null; onChange: (m: GoalMinutes) => void }) {
  return (
    <div className="goal-picker" role="radiogroup" aria-label="Daily goal in minutes">
      {GOAL_OPTIONS.map((m) => (
        <button key={m} type="button" role="radio" aria-checked={value === m} className={cx("goal-option", value === m && "selected")} onClick={() => onChange(m)}>
          <strong>{m}</strong>
          <span className="xs">min / day</span>
          <span className="xs muted">{LABELS[m]}</span>
        </button>
      ))}
    </div>
  );
}
