import type { ReactNode } from "react";
import { Emoji } from "../../../shared/illustrations/Emoji";
import type { IconName } from "../../../shared/illustrations/Emoji";
import "./study-components.css";

export type ResultTone = "amber" | "green" | "blue" | "teal" | "purple";

/** Celebration-screen stat card: coloured header label, emoji + big number (lesson, review, test). */
export function ResultStat({ tone, label, emoji, value }: { tone: ResultTone; label: string; emoji: IconName; value: ReactNode }) {
  return (
    <div className={`result-stat result-stat-${tone}`}>
      <div className="result-stat-head">{label}</div>
      <div className="result-stat-body">
        <Emoji name={emoji} size={26} />
        <span>{value}</span>
      </div>
    </div>
  );
}
