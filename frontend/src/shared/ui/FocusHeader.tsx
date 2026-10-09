import { X } from "lucide-react";
import type { ReactNode } from "react";
import { Link } from "react-router";
import { ProgressBar } from "./Progress";

/**
 * Top bar of focus-mode screens (lessons, tests, reviews): exit, progress, and a right slot.
 * Navigation is hidden in focus mode so the learner only sees the task (cognitive load).
 */
export function FocusHeader({ exitTo, exitLabel = "Exit", progress, progressLabel, right }: { exitTo: string; exitLabel?: string; progress: number; progressLabel: string; right?: ReactNode }) {
  return (
    <header className="focus-header">
      <Link to={exitTo} className="icon-btn" aria-label={exitLabel} title={exitLabel}>
        <X size={24} strokeWidth={3} />
      </Link>
      <div className="focus-progress">
        <ProgressBar value={progress} label={progressLabel} size="lg" />
      </div>
      <div className="focus-right">{right}</div>
    </header>
  );
}
