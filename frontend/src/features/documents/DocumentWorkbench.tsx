import { AnimatePresence, motion, useReducedMotion } from "motion/react";
import { useEffect, useState } from "react";
import { Sage } from "../../shared/illustrations/Sage";
import { Emoji } from "../../shared/illustrations/Emoji";
import { cx } from "../../shared/ui/cx";
import { stageProgress, workStage } from "./pipeline";
import type { DocumentDto } from "./types";

type Doc = Pick<DocumentDto, "status" | "textPages" | "retryAt">;

/**
 * Sapi at work on a document that is still processing: a stage-specific prop orbits Sapi, a line describing
 * the concrete work rotates, and a bar creeps through the current stage. Showing the work (rather than a bare
 * spinner) makes the wait feel shorter and the result more valuable; reduced-motion users get a still scene.
 */
export function DocumentWorkbench({ doc, compact }: { doc: Doc; compact?: boolean }) {
  const stage = workStage(doc);
  const reduceMotion = useReducedMotion();
  const [line, setLine] = useState(0);
  const [seconds, setSeconds] = useState(0);

  // Restart the rotation and the in-stage creep whenever the stage changes.
  const stageKey = `${doc.status}:${doc.retryAt ?? ""}`;
  useEffect(() => {
    setLine(0);
    setSeconds(0);
    const tick = setInterval(() => setSeconds((s) => s + 1), 1000);
    const rotate = setInterval(() => setLine((i) => i + 1), 3500);
    return () => {
      clearInterval(tick);
      clearInterval(rotate);
    };
  }, [stageKey]);

  const text = stage.lines[line % stage.lines.length];
  const progress = stageProgress(stage.band, seconds);
  const paused = stage.mood === "sleepy";

  return (
    <div className={cx("workbench", compact && "workbench-compact", paused && "is-paused")} role="status" aria-live="polite">
      <div className="workbench-scene" aria-hidden>
        <Sage mood={stage.mood} size={compact ? 48 : 84} still={!!reduceMotion} />
        <span className="workbench-prop" key={stage.emoji}>
          <Emoji name={stage.emoji} size={compact ? 22 : 34} />
        </span>
        {!paused && <span className="workbench-sparkle" />}
      </div>
      <div className="workbench-body">
        <AnimatePresence mode="wait" initial={false}>
          <motion.p
            key={text}
            className="workbench-line"
            initial={reduceMotion ? false : { opacity: 0, y: 6 }}
            animate={{ opacity: 1, y: 0 }}
            exit={reduceMotion ? undefined : { opacity: 0, y: -6 }}
            transition={{ duration: 0.25 }}
          >
            {text}
          </motion.p>
        </AnimatePresence>
        <div
          className="workbench-bar"
          role="progressbar"
          aria-label="Processing progress"
          aria-valuemin={0}
          aria-valuemax={100}
          aria-valuenow={Math.round(progress * 100)}
        >
          <div className="workbench-fill" style={{ width: `${progress * 100}%` }} />
        </div>
      </div>
    </div>
  );
}
