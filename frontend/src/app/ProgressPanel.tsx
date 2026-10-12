import { Link } from "react-router";
import { useProgress } from "../features/progress/ProgressContext";
import { goalProgress, levelProgress, streakMessage } from "../features/progress/levels";
import { Emoji } from "../shared/illustrations/Emoji";
import { Card } from "../shared/ui/Card";
import { ProgressBar, ProgressRing } from "../shared/ui/Progress";
import { useReviewSummary } from "./useReviewSummary";

/**
 * Streak, daily goal, level and reviews. Lives in the right rail on wide screens; pages may also
 * render it inline (e.g. Home on narrow screens) with `className="progress-panel-inline"`.
 */
export function ProgressPanel({ className }: { className?: string }) {
  const { summary } = useProgress();
  const reviews = useReviewSummary();
  if (!summary) return null;
  const { streak, goal, xp } = summary;
  return (
    <div className={className ? `progress-panel ${className}` : "progress-panel"}>
      <Card tight>
        <div className="panel-row">
          <Emoji name="fire" size={40} />
          <div>
            <div className="panel-big">
              {streak.current} <span className="panel-unit">day{streak.current === 1 ? "" : "s"}</span>
            </div>
            <p className="small muted">{streakMessage(streak)}</p>
          </div>
        </div>
      </Card>
      <Card tight>
        <div className="panel-row">
          <ProgressRing value={goalProgress(goal)} size={64} stroke={9} label="Daily goal">
            <Emoji name={goal.reached ? "check-mark" : "bullseye"} size={26} />
          </ProgressRing>
          <div>
            <p className="eyebrow">Daily goal</p>
            <div className="panel-big">
              {Math.min(goal.todayMinutes, goal.dailyMinutes)}
              <span className="panel-unit"> / {goal.dailyMinutes} min</span>
            </div>
            <Link to="/welcome" className="small">
              Change goal
            </Link>
          </div>
        </div>
      </Card>
      <Card tight>
        <div className="row-between">
          <div className="row">
            <Emoji name="crown" size={28} />
            <strong>Level {xp.level}</strong>
          </div>
          <span className="small muted tabular">{xp.total} XP</span>
        </div>
        <div style={{ marginTop: "var(--space-3)" }}>
          <ProgressBar value={levelProgress(xp)} color="var(--purple-main)" label={`Progress to level ${xp.level + 1}`} />
        </div>
        <p className="xs muted" style={{ marginTop: "var(--space-2)" }}>
          {xp.nextLevelXp - xp.total} XP to level {xp.level + 1}
        </p>
      </Card>
      {reviews && reviews.dueNow > 0 && (
        <Link to="/recall" className="card card-tight card-interactive card-teal">
          <div className="row">
            <Emoji name="recall" size={32} />
            <div>
              <strong>
                {reviews.dueNow} card{reviews.dueNow === 1 ? "" : "s"} to recall
              </strong>
              <p className="small">~{Math.max(1, Math.round((reviews.dueNow * 20) / 60))} min · keeps memories fresh</p>
            </div>
          </div>
        </Link>
      )}
    </div>
  );
}
