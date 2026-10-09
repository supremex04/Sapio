import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { celebrate } from "../../shared/lib/celebrate";
import { useToast } from "../../shared/ui/Toast";
import { progressApi } from "./api";
import { ProgressContext } from "./ProgressContext";
import type { ProgressSummary } from "./types";

/**
 * Holds the learner's progress summary. XP is announced modestly (a small toast), while
 * level-ups and goal completion get a celebration - achievement moments, not noise.
 */
export function ProgressProvider({ children }: { children: ReactNode }) {
  const toast = useToast();
  const [summary, setSummary] = useState<ProgressSummary | null>(null);
  const previous = useRef<ProgressSummary | null>(null);

  const refresh = useCallback(async () => {
    try {
      const next = await progressApi.summary();
      const prev = previous.current;
      if (prev) {
        const gained = next.xp.total - prev.xp.total;
        if (next.xp.level > prev.xp.level) {
          toast.show("level", <span>Level {next.xp.level} reached. Your knowledge is compounding.</span>, 5000);
          celebrate("big");
        } else if (gained > 0) {
          toast.show("xp", (
            <span>
              <strong>+{gained} XP</strong> <span className="muted">· keep it up</span>
            </span>
          ));
        }
        if (next.goal.reached && !prev.goal.reached) {
          toast.show("success", <span>Daily goal reached: {next.goal.dailyMinutes} minutes of focused learning.</span>, 5000);
          celebrate("small");
        }
      }
      previous.current = next;
      setSummary(next);
    } catch {
      // progress is non-critical; keep the last known summary
    }
  }, [toast]);

  useEffect(() => {
    void refresh();
    const id = window.setInterval(() => void refresh(), 60_000); // active minutes tick up
    return () => window.clearInterval(id);
  }, [refresh]);

  const value = useMemo(() => ({ summary, refresh }), [summary, refresh]);
  return <ProgressContext.Provider value={value}>{children}</ProgressContext.Provider>;
}
