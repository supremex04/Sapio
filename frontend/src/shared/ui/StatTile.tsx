import type { ReactNode } from "react";
import { Emoji } from "../illustrations/Emoji";
import type { EmojiName } from "../illustrations/emojiNames";

/** Emoji + big number + label: streak, XP, goal, reviews… */
export function StatTile({ emoji, value, label, children }: { emoji: EmojiName; value: ReactNode; label: string; children?: ReactNode }) {
  return (
    <div className="stat-tile">
      <Emoji name={emoji} size={36} />
      <div style={{ minWidth: 0 }}>
        <div className="stat-tile-value">{value}</div>
        <div className="stat-tile-label">{label}</div>
        {children}
      </div>
    </div>
  );
}
