import { ArrowRight, Target } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { firstName } from "../../shared/lib/format";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { PageTransition } from "../../shared/ui/PageTransition";
import { useAuth } from "../auth/AuthContext";
import { progressApi } from "./api";
import { GoalPicker } from "./GoalPicker";
import { markOnboarded } from "./onboarding";
import { useProgress } from "./ProgressContext";
import type { GoalMinutes } from "./types";
import "./progress.css";

/**
 * One light, skippable onboarding step: an implementation intention ("I'll study 10 minutes a
 * day"), which strongly predicts follow-through (learning-science.md §2).
 */
export function WelcomePage() {
  const { user } = useAuth();
  const { summary, refresh } = useProgress();
  const navigate = useNavigate();
  const [goal, setGoal] = useState<GoalMinutes | null>(null);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const current = goal ?? summary?.goal.dailyMinutes ?? 10;

  function finish() {
    if (user) markOnboarded(user.id);
    navigate("/", { replace: true });
  }

  async function save() {
    setSaving(true);
    setError(null);
    try {
      await progressApi.setGoal(current as GoalMinutes);
      await refresh();
      finish();
    } catch (e) {
      setError(errorMessage(e));
      setSaving(false);
    }
  }

  return (
    <PageTransition>
      <div className="welcome">
        <Card className="welcome-card">
          <div className="welcome-icon" aria-hidden>
            <Target size={28} />
          </div>
          <h1>{user ? `Hi ${firstName(user.fullName)}, let's set a rhythm` : "Set your daily rhythm"}</h1>
          <p className="muted">
            Small, regular sessions beat long cramming. Pick an amount that feels easy; you can change it any time.
          </p>
          <p className="welcome-intention">
            I'll study <strong>{current} minutes</strong> a day.
          </p>
          <GoalPicker value={current} onChange={setGoal} />
          {error && <Alert>{error}</Alert>}
          <div className="row" style={{ justifyContent: "center" }}>
            <Button variant="primary" size="lg" loading={saving} onClick={save} iconRight={<ArrowRight size={18} />}>
              Save my goal
            </Button>
            <Button variant="ghost" size="lg" onClick={finish}>
              Skip for now
            </Button>
          </div>
        </Card>
      </div>
    </PageTransition>
  );
}
