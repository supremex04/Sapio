import { AlertTriangle, Check } from "lucide-react";
import { motion } from "motion/react";
import { cx } from "../../shared/ui/cx";
import { PIPELINE_STEPS, pipelineState } from "./pipeline";
import type { DocumentDto } from "./types";

export function PipelineStepper({ doc, compact }: { doc: Pick<DocumentDto, "status" | "textPages">; compact?: boolean }) {
  const state = pipelineState(doc);
  return (
    <ol className={cx("pipeline", compact && "pipeline-compact")} aria-label={`Processing: ${state.label}`}>
      {PIPELINE_STEPS.map((label, i) => {
        const done = i < state.current || (state.done && i === state.current);
        const active = i === state.current && !state.done;
        const failed = active && state.failed;
        return (
          <li key={label} className={cx("pipeline-step", done && "done", active && "active", failed && "failed")} aria-current={active ? "step" : undefined}>
            <span className="pipeline-dot" aria-hidden>
              {done ? <Check size={compact ? 10 : 14} strokeWidth={3} /> : failed ? <AlertTriangle size={compact ? 10 : 13} /> : active ? <span className="pipeline-pulse" /> : null}
            </span>
            {!compact && <span className="pipeline-label">{label}</span>}
            {i < PIPELINE_STEPS.length - 1 && (
              <span className="pipeline-line" aria-hidden>
                <motion.span initial={false} animate={{ scaleX: done ? 1 : 0 }} transition={{ duration: 0.5 }} />
              </span>
            )}
          </li>
        );
      })}
    </ol>
  );
}
