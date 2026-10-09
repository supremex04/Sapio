import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";

const TIPS = [
  "Trying to recall something, even unsuccessfully, makes it stick better than re-reading.",
  "Short, spaced sessions beat one long cram.",
  "Explaining an idea in your own words is one of the fastest ways to understand it.",
  "Mistakes you make with confidence, once corrected, are remembered especially well.",
  "Mixing topics when practising helps you tell similar ideas apart.",
  "Concrete examples turn abstract ideas into something you can hold on to.",
];

/** Friendly wait while the AI builds a lesson or test (10–40 s). */
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
      <div className="generating-orb" aria-hidden>
        <span />
        <span />
        <span />
      </div>
      <h2>{title}</h2>
      <p className="muted">{subtitle}</p>
      <div className="generating-tip">
        <span className="eyebrow">Did you know</span>
        <AnimatePresence mode="wait">
          <motion.p key={tip} initial={{ opacity: 0, y: 6 }} animate={{ opacity: 1, y: 0 }} exit={{ opacity: 0, y: -6 }} transition={{ duration: 0.3 }}>
            {TIPS[tip]}
          </motion.p>
        </AnimatePresence>
      </div>
      {seconds > 20 && <p className="xs muted">Still working. Longer documents take a little more time.</p>}
    </div>
  );
}
