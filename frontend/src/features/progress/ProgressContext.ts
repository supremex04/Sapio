import { createContext, useContext } from "react";
import type { ProgressSummary } from "./types";

export interface ProgressContextValue {
  summary: ProgressSummary | null;
  /** Refetches the summary and announces XP gained / level-ups since the last fetch. */
  refresh: () => Promise<void>;
}

export const ProgressContext = createContext<ProgressContextValue | null>(null);

export function useProgress(): ProgressContextValue {
  const ctx = useContext(ProgressContext);
  if (!ctx) throw new Error("useProgress must be used inside <ProgressProvider>");
  return ctx;
}
