import { Check } from "lucide-react";
import { cx } from "../../shared/ui/cx";
import { pipelineState, pipelineSteps } from "./pipeline";
import type { DocumentDto } from "./types";

/**
 * Chunky step dots: Uploaded (checked) → Reading text → Building your course → Ready.
 * "Uploaded" is always done (endowed progress). The active dot pulses (reduced-motion aware).
 */
export function PipelineStepper({ doc, compact }: { doc: Pick<DocumentDto, "status" | "textPages" | "retryAt">; compact?: boolean }) {
  const state = pipelineState(doc);
  return (
    <div className={cx("pipeline", compact && "pipeline-compact")}>
      <ol className="pipeline-steps" aria-label={`Processing: ${state.label}`}>
        {pipelineSteps(doc).map((label, i) => {
          const done = i < state.current || (state.done && i === state.current);
          const active = i === state.current && !state.done;
          const failed = active && state.failed;
          return (
            <li key={label} className={cx("pipeline-step", done && "is-done", active && !failed && "is-active", failed && "is-failed")} aria-current={active ? "step" : undefined}>
              <span className="pipeline-dot" aria-hidden>
                {done ? <Check size={16} strokeWidth={3.5} /> : failed ? "!" : i + 1}
              </span>
              {!compact && <span className="pipeline-label">{label}</span>}
              <span className="visually-hidden">
                {compact ? label : ""}
                {done ? " (done)" : failed ? " (failed)" : active ? " (in progress)" : ""}
              </span>
            </li>
          );
        })}
      </ol>
      {compact && <span className={cx("pipeline-current", state.failed && "is-failed")}>{state.label}</span>}
    </div>
  );
}
