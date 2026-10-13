import { ArrowRight } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { firstName } from "../../shared/lib/format";
import { Sage } from "../../shared/illustrations/Sage";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { PageTransition } from "../../shared/ui/PageTransition";
import { SpeechBubble } from "../../shared/ui/SpeechBubble";
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
        <div className="welcome-hello">
          <Sage mood="happy" size={104} />
          <SpeechBubble>
            {user ? `Hi ${firstName(user.fullName)}! ` : ""}How much time can you give each day?
          </SpeechBubble>
        </div>
        <p className="muted welcome-note">
          Small, regular sessions beat long cramming. Pick something that feels easy; you can change it any time.
        </p>
        <GoalPicker value={current} onChange={setGoal} />
        <p className="welcome-intention" aria-live="polite">
          I'll study <strong>{current} minutes</strong> a day.
        </p>
        {error && <Alert>{error}</Alert>}
        <div className="welcome-actions">
          <Button variant="ghost" onClick={finish}>
            Maybe later
          </Button>
          <Button variant="primary" size="lg" loading={saving} onClick={save} iconRight={<ArrowRight size={20} />}>
            Continue
          </Button>
        </div>
      </div>
    </PageTransition>
  );
}
