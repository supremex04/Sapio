import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { Dia } from "../../../shared/illustrations/Dia";
import { Emoji } from "../../../shared/illustrations/Emoji";
import { ProgressBar } from "../../../shared/ui/Progress";
import { SpeechBubble } from "../../../shared/ui/SpeechBubble";
import "./study-components.css";

const TIPS = [
  "Trying to recall something, even unsuccessfully, makes it stick better than re-reading.",
  "Short, spaced sessions beat one long cram.",
  "Explaining an idea in your own words is one of the fastest ways to understand it.",
  "Mistakes you make with confidence, once corrected, are remembered especially well.",
  "Mixing topics when practising helps you tell similar ideas apart.",
  "Concrete examples turn abstract ideas into something you can hold on to.",
];

/** Estimated progress for a 10–40 s generation: rises quickly, then eases towards 92% (never "done" early). */
export function estimatedProgress(seconds: number): number {
  return 0.92 * (1 - Math.exp(-seconds / 14));
}

/** Friendly wait while the AI builds a lesson, test or report (10–40 s): Dia thinks, tips rotate. */
export function GeneratingState({ title, subtitle }: { title: string; subtitle: string }) {
  const [tip, setTip] = useState(() => Math.floor(Math.random() * TIPS.length));
  const [seconds, setSeconds] = useState(0);
  useEffect(() => {
    const t = setInterval(() => setTip((i) => (i + 1) % TIPS.length), 6000);
    const s = setInterval(() => setSeconds((n) => n + 1), 1000);
    return () => {
      clearInterval(t);
      clearInterval(s);
    };
  }, []);

  return (
    <div className="generating" role="status" aria-live="polite">
      <div className="generating-dia">
        <Dia mood="thinking" size={120} />
      </div>
      <h2>{title}</h2>
      <p className="muted">{subtitle}</p>
      <div className="generating-bar">
        <ProgressBar value={estimatedProgress(seconds)} label="Preparing" color="var(--blue-main)" />
      </div>
      {seconds > 20 && <p className="xs muted">Still working. Longer documents take a little more time.</p>}
      <SpeechBubble tail="up" className="generating-tip">
        <span className="eyebrow">
          <Emoji name="light-bulb" size={18} /> Did you know
        </span>
        <AnimatePresence mode="wait">
          <motion.p key={tip} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.3 }}>
            {TIPS[tip]}
          </motion.p>
        </AnimatePresence>
      </SpeechBubble>
    </div>
  );
}
