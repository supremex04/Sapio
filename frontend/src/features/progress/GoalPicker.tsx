import { Emoji } from "../../shared/illustrations/Emoji";
import type { IconName } from "../../shared/illustrations/Emoji";
import { cx } from "../../shared/ui/cx";
import { GOAL_OPTIONS, type GoalMinutes } from "./types";

const OPTIONS: Record<GoalMinutes, { label: string; emoji: IconName }> = {
  5: { label: "Casual", emoji: "seedling" },
  10: { label: "Regular", emoji: "light-bulb" },
  15: { label: "Serious", emoji: "fire" },
  20: { label: "Intense", emoji: "flexed-biceps" },
  30: { label: "Ambitious", emoji: "rocket" },
};

/** Big selectable 3D tiles: an implementation intention ("I'll study 10 minutes a day"). */
export function GoalPicker({ value, onChange }: { value: number | null; onChange: (m: GoalMinutes) => void }) {
  return (
    <div className="goal-picker" role="radiogroup" aria-label="Daily goal in minutes">
      {GOAL_OPTIONS.map((m) => (
        <button key={m} type="button" role="radio" aria-checked={value === m} className={cx("goal-option", value === m && "is-selected")} onClick={() => onChange(m)}>
          <Emoji name={OPTIONS[m].emoji} size={32} />
          <span className="goal-option-minutes">{m} min</span>
          <span className="goal-option-label">{OPTIONS[m].label}</span>
        </button>
      ))}
    </div>
  );
}
