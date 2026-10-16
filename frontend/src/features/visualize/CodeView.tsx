import { ChevronLeft, ChevronRight } from "lucide-react";
import { useMemo, useState } from "react";
import { cx } from "../../shared/ui/cx";
import { lineRange, stepsPerBeat } from "./progress";
import type { SceneClock } from "./NarratedScene";
import type { Beat, CodeScene } from "./types";

/**
 * A program traced line by line: the line running, the call stack with each frame's variables, and the output so
 * far. The narration walks through the steps on each beat's lines; paused, the learner steps by hand.
 */
export function CodeView({ scene, beats, clock }: { scene: CodeScene; beats: Beat[]; clock: SceneClock }) {
  const steps = scene.trace.steps;
  const lines = useMemo(() => scene.program.replace(/\n$/, "").split("\n"), [scene.program]);
  // Line ranges checked against the program traced (older visualizations: the storyboard's own).
  const ranges = useMemo(() => (scene.lines ? scene.lines.map((l) => lineRange(l)) : beats.map((b) => lineRange(b.focus))), [scene.lines, beats]);
  const perBeat = useMemo(() => stepsPerBeat(ranges, steps), [ranges, steps]);
  const [manual, setManual] = useState<number | null>(null);

  // The step the narration is at: through this beat's steps as it plays, else the last step shown before it.
  let narrated = -1;
  const mine = perBeat[clock.beat] ?? [];
  if (mine.length) narrated = mine[Math.min(mine.length - 1, Math.floor(clock.progress * mine.length))] ?? -1;
  else {
    for (let b = clock.beat - 1; b >= 0 && narrated < 0; b--) {
      const earlier = perBeat[b] ?? [];
      narrated = earlier[earlier.length - 1] ?? -1;
    }
  }
  const index = clock.playing || manual == null ? narrated : manual;
  const step = index >= 0 ? steps[index] : undefined;
  const range = ranges[clock.beat];

  const go = (to: number) => setManual(Math.max(0, Math.min(steps.length - 1, to)));

  return (
    <div className="viz-code-view">
      <ol className="viz-listing" aria-label="Program">
        {lines.map((text, i) => {
          const n = i + 1;
          return (
            <li key={n} className={cx(step?.line === n && "is-current", range && n >= range[0] && n <= range[1] && "is-explained")} aria-current={step?.line === n ? "step" : undefined}>
              <span className="viz-ln" aria-hidden>
                {n}
              </span>
              <code>{text || " "}</code>
            </li>
          );
        })}
      </ol>
      <div className="viz-state">
        <div className="row-between">
          <strong className="small">
            {step ? `Step ${index + 1} of ${steps.length}${scene.trace.truncated ? "+" : ""}` : "Not started"}
          </strong>
          <span className="row">
            <button type="button" className="viz-step" onClick={() => go((index < 0 ? 0 : index) - 1)} disabled={clock.playing || index <= 0} aria-label="Previous step">
              <ChevronLeft size={16} />
            </button>
            <button type="button" className="viz-step" onClick={() => go(index + 1)} disabled={clock.playing || index >= steps.length - 1} aria-label="Next step">
              <ChevronRight size={16} />
            </button>
          </span>
        </div>
        {step?.event === "return" && <p className="xs muted">Returns {step.value}</p>}
        {step?.event === "exception" && <p className="xs viz-error-text">Raises {step.value}</p>}
        <div className="viz-frames">
          {(step?.stack ?? []).map((f, i) => (
            <div key={i} className="viz-frame">
              <span className="xs muted">{f.name === "main" ? "Program" : `${f.name}()`}</span>
              {Object.keys(f.vars).length === 0 ? (
                <span className="xs muted">no variables yet</span>
              ) : (
                <dl>
                  {Object.entries(f.vars).map(([k, v]) => (
                    <div key={k}>
                      <dt>{k}</dt>
                      <dd>{v}</dd>
                    </div>
                  ))}
                </dl>
              )}
            </div>
          ))}
        </div>
        <div className="viz-output">
          <span className="xs muted">Output</span>
          <pre>{step?.output ? step.output : <span className="muted">nothing printed yet</span>}</pre>
          {scene.trace.error && index === steps.length - 1 && <p className="xs viz-error-text">{scene.trace.error}</p>}
        </div>
      </div>
    </div>
  );
}
