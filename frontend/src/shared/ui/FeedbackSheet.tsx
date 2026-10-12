import { motion } from "motion/react";
import type { ReactNode } from "react";
import { Dia, type DiaMood } from "../illustrations/Dia";
import { cx } from "./cx";

export type SheetTone = "neutral" | "correct" | "partial" | "incorrect";

interface Props {
  tone: SheetTone;
  title?: ReactNode;
  mood?: DiaMood;
  children?: ReactNode;
  /** Right-aligned primary action(s), e.g. Check / Continue. */
  actions: ReactNode;
  /** Left side of the action row, e.g. a "Try again" link or review note. */
  aside?: ReactNode;
  /** Render in the flow instead of fixed to the bottom (style guide, previews). */
  inline?: boolean;
}

/**
 * The sticky bottom bar of every question flow. Neutral while answering (holds "Check", "Submit" or "Next"), then
 * slides up in the verdict colour with Dia's reaction and the explanation (immediate, elaborative
 * feedback). One pattern for lessons, tests and reviews.
 */
export function FeedbackSheet({ tone, title, mood, children, actions, aside, inline }: Props) {
  const verdict = tone !== "neutral";
  return (
    <motion.div
      key={tone}
      className={cx("sheet", verdict && `sheet-${tone}`, inline && "sheet-inline")}
      role={verdict ? "status" : undefined}
      aria-live={verdict ? "polite" : undefined}
      initial={verdict ? { y: 40, opacity: 0 } : false}
      animate={{ y: 0, opacity: 1 }}
      transition={{ type: "spring", stiffness: 420, damping: 34 }}
    >
      <div className="sheet-inner">
        {verdict && (
          <div className="sheet-head">
            {mood && <Dia mood={mood} size={56} />}
            <div className="sheet-title">{title}</div>
          </div>
        )}
        {children && <div className="sheet-body">{children}</div>}
        <div className="sheet-actions">
          <div>{aside}</div>
          <div className="row">{actions}</div>
        </div>
      </div>
    </motion.div>
  );
}
