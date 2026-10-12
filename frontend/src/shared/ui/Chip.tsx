import type { ReactNode } from "react";
import { Emoji } from "../illustrations/Emoji";
import type { IconName } from "../illustrations/Emoji";
import { cx } from "./cx";

export type ChipTone = "neutral" | "xp" | "streak" | "mastery" | "review";

/** Small pill for counts (XP, streak, due cards). Pass `onClick` to make it a toggle (aria-pressed). */
export function Chip({ emoji, tone = "neutral", children, label, pressed, onClick }: { emoji?: IconName; tone?: ChipTone; children: ReactNode; label?: string; pressed?: boolean; onClick?: () => void }) {
  const className = cx("chip", tone !== "neutral" && `chip-${tone}`);
  const content = (
    <>
      {emoji && <Emoji name={emoji} size={20} />}
      {children}
    </>
  );
  return onClick ? (
    <button type="button" className={className} aria-pressed={pressed} aria-label={label} onClick={onClick}>
      {content}
    </button>
  ) : (
    <span className={className} aria-label={label} title={label}>
      {content}
    </span>
  );
}
