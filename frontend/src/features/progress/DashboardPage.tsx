import { ArrowRight, Upload } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { ProgressPanel } from "../../app/ProgressPanel";
import { Emoji } from "../../shared/illustrations/Emoji";
import type { EmojiName } from "../../shared/illustrations/emojiNames";
import { firstName, formatRelative, greeting } from "../../shared/lib/format";
import { ButtonLink } from "../../shared/ui/Button";
import { Card, CardHeader } from "../../shared/ui/Card";
import { Chip } from "../../shared/ui/Chip";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { ProgressBar } from "../../shared/ui/Progress";
import { Skeleton } from "../../shared/ui/Skeleton";
import { useAuth } from "../auth/AuthContext";
import { documentsApi } from "../documents/api";
import { displayTitle, type DocumentDto } from "../documents/types";
import { studyApi } from "../study/api";
import { nextStepCta, nextStepHref } from "../study/nextStep";
import type { Material, NextStep, NextStepKind } from "../study/types";
import { ActivityChart } from "./ActivityChart";
import { progressApi } from "./api";
import { streakMessage } from "./levels";
import { useProgress } from "./ProgressContext";
import type { ActivityItem, DailyActivity } from "./types";
import "./progress.css";

interface ContinueItem {
  doc: DocumentDto;
  material: Material | null;
}

function timeOfDayEmoji(date = new Date()): EmojiName {
  const h = date.getHours();
  if (h >= 5 && h < 12) return "sunrise";
  if (h >= 12 && h < 18) return "sun";
  return "crescent-moon";
}

export function DashboardPage() {
  const { user } = useAuth();
  const { summary } = useProgress();
  const [nextStep, setNextStep] = useState<NextStep | null | undefined>(undefined);
  const [days, setDays] = useState<DailyActivity[] | null>(null);
  const [activity, setActivity] = useState<ActivityItem[] | null>(null);
  const [continueItems, setContinueItems] = useState<ContinueItem[] | null>(null);

  useEffect(() => {
    studyApi.nextStep().then(setNextStep, () => setNextStep(null));
    progressApi.daily(14).then((r) => setDays(r.days), () => setDays([]));
    progressApi.activity(8).then((r) => setActivity(r.items), () => setActivity([]));
    documentsApi
      .list(0, 12)
      .then(async (page) => {
        const ready = page.items.filter((d) => d.status === "READY").slice(0, 4);
        const materials = await Promise.all(ready.map((d) => studyApi.material(d.id).catch(() => null)));
        setContinueItems(ready.map((doc, i) => ({ doc, material: materials[i] ?? null })));
      })
      .catch(() => setContinueItems([]));
  }, []);

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="dash-header">
          <Emoji name={timeOfDayEmoji()} size={48} />
          <div>
            <h1>
              {greeting()}
              {user ? `, ${firstName(user.fullName)}` : ""}!
            </h1>
            {summary && <p className="muted">{streakMessage(summary.streak)}</p>}
          </div>
        </header>

        <NextStepCard step={nextStep} />

        <ProgressPanel className="progress-panel-inline" />

        <section className="stack">
          <div className="row-between">
            <h2>Continue learning</h2>
            <Link to="/library" className="small">
              See library
            </Link>
          </div>
          <ContinueLearning items={continueItems} />
        </section>

        <Card>{days ? <ActivityChart days={days} goalMinutes={summary?.goal.dailyMinutes ?? null} /> : <Skeleton height={260} />}</Card>

        <Card>
          <CardHeader icon={<Emoji name="chart-increasing" size={24} />} title="Recent activity" />
          <ActivityFeed items={activity} />
        </Card>
      </div>
    </PageTransition>
  );
}

/** One recommended action (avoids choice overload; learning-science.md §2). Hue = the job. */
const STEP_STYLE: Record<NextStepKind, { emoji: EmojiName; tone: string; button: "review" | "primary" | "mastery" | "accent" }> = {
  REVIEW: { emoji: "repeat-button", tone: "teal", button: "review" },
  CONTINUE_TOPIC: { emoji: "open-book", tone: "blue", button: "primary" },
  START_TOPIC: { emoji: "light-bulb", tone: "blue", button: "primary" },
  TAKE_TEST: { emoji: "bullseye", tone: "purple", button: "mastery" },
  UPLOAD: { emoji: "page-facing-up", tone: "amber", button: "accent" },
};

const FALLBACK_STEP: NextStep = {
  kind: "UPLOAD",
  documentId: null,
  topicId: null,
  title: "Open your library",
  reason: "Pick up any study guide, or add new material to learn from.",
  estimatedMinutes: null,
};

function NextStepCard({ step: loaded }: { step: NextStep | null | undefined }) {
  if (loaded === undefined) return <Skeleton height={176} radius="var(--radius-xl)" />;
  const step = loaded ?? FALLBACK_STEP;
  const style = STEP_STYLE[step.kind];
  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}>
      <Link to={nextStepHref(step)} className={`next-step next-step-${style.tone}`} aria-label={`${nextStepCta(step)}: ${step.title}`}>
        <div className="next-step-art" aria-hidden>
          <Emoji name={style.emoji} size={72} />
        </div>
        <div className="next-step-body">
          <p className="next-step-eyebrow">Your next step</p>
          <h2>{step.title}</h2>
          <p className="next-step-reason">{step.reason}</p>
          <div className="next-step-foot">
            {step.estimatedMinutes != null && (
              <Chip emoji="alarm-clock" label={`About ${step.estimatedMinutes} minutes`}>
                ~{step.estimatedMinutes} min
              </Chip>
            )}
            <span className={`btn btn-lg btn-${style.button} next-step-btn`}>
              {nextStepCta(step)} <ArrowRight size={20} aria-hidden />
            </span>
          </div>
        </div>
      </Link>
    </motion.div>
  );
}

const ACTIVITY: Record<string, { label: string; emoji: EmojiName }> = {
  LESSON_STARTED: { label: "Started a lesson", emoji: "open-book" },
  LESSON_COMPLETED: { label: "Completed a topic", emoji: "graduation-cap" },
  TOPIC_COMPLETED: { label: "Completed a topic", emoji: "graduation-cap" },
  CHECK_ANSWERED: { label: "Answered a check", emoji: "check-mark-button" },
  ASSESSMENT_ANSWERED: { label: "Answered a test question", emoji: "memo" },
  ASSESSMENT_STARTED: { label: "Started a test", emoji: "bullseye" },
  ASSESSMENT_COMPLETED: { label: "Finished a test", emoji: "trophy" },
  REVIEW_ANSWERED: { label: "Reviewed a card", emoji: "repeat-button" },
  TUTOR_QUESTION: { label: "Asked the tutor", emoji: "thinking-face" },
  DAILY_GOAL_REACHED: { label: "Reached the daily goal", emoji: "party-popper" },
};

function activityMeta(type: string): { label: string; emoji: EmojiName } {
  return ACTIVITY[type] ?? { label: type.toLowerCase().replace(/_/g, " ").replace(/^./, (c) => c.toUpperCase()), emoji: "sparkles" };
}

function ActivityFeed({ items }: { items: ActivityItem[] | null }) {
  if (!items) {
    return (
      <div className="stack-sm">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} height={44} />
        ))}
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <EmptyState icon={<Emoji name="sparkles" size={48} />} title="Nothing yet">
        Your learning moments will show up here.
      </EmptyState>
    );
  }
  return (
    <ul className="feed">
      {items.map((item, i) => {
        const meta = activityMeta(item.type);
        return (
          <li key={`${item.occurredAt}-${i}`}>
            <span className="feed-icon">
              <Emoji name={meta.emoji} size={26} />
            </span>
            <div className="feed-body">
              <p>
                <strong>{meta.label}</strong>
                {item.topicTitle && <span className="text-2"> · {item.topicTitle}</span>}
              </p>
              <p className="xs muted">
                {item.documentTitle && `${item.documentTitle} · `}
                {formatRelative(item.occurredAt)}
              </p>
            </div>
            {item.xp > 0 && (
              <Chip emoji="high-voltage" tone="xp" label={`${item.xp} XP`}>
                +{item.xp}
              </Chip>
            )}
          </li>
        );
      })}
    </ul>
  );
}

/** Mean mastery of the topics a learner has practised (null until any). */
function meanMastery(material: Material | null): number | null {
  const values = material?.topics.map((t) => t.mastery).filter((m): m is number => m != null) ?? [];
  return values.length ? values.reduce((a, b) => a + b, 0) / values.length : null;
}

function ContinueLearning({ items }: { items: ContinueItem[] | null }) {
  if (!items) {
    return (
      <div className="continue-grid">
        {[0, 1].map((i) => (
          <Skeleton key={i} height={168} radius="var(--radius-lg)" />
        ))}
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <Card>
        <EmptyState
          mood="happy"
          title="Bring your first document"
          action={
            <ButtonLink to="/library" variant="primary" icon={<Upload size={18} />}>
              Upload material
            </ButtonLink>
          }
        >
          A syllabus, textbook chapter or lecture notes. Ved turns it into lessons and practice in a minute or two.
        </EmptyState>
      </Card>
    );
  }
  return (
    <div className="continue-grid">
      {items.map(({ doc, material }) => {
        const done = material?.stats.completedTopics ?? 0;
        const total = material?.stats.topicCount ?? 0;
        const mastery = meanMastery(material);
        return (
          <Link key={doc.id} to={`/documents/${doc.id}`} className="card card-interactive continue-card">
            <div className="row-between">
              <Emoji name="books" size={40} />
              <div className="row" style={{ gap: "var(--space-1)" }}>
                {material && material.stats.reviewsDue > 0 && (
                  <Chip emoji="repeat-button" tone="review" label={`${material.stats.reviewsDue} reviews due`}>
                    {material.stats.reviewsDue}
                  </Chip>
                )}
                {mastery != null && (
                  <Chip emoji="brain" tone="mastery" label={`${Math.round(mastery * 100)}% mastery`}>
                    {Math.round(mastery * 100)}%
                  </Chip>
                )}
              </div>
            </div>
            <h3 className="break continue-title">{material?.title ?? displayTitle(doc)}</h3>
            <div className="stack-sm" style={{ marginTop: "auto" }}>
              <ProgressBar value={total ? done / total : 0} label="Topics completed" />
              <p className="small muted">
                {done} of {total} topics
                {material?.stats.bestScore != null && ` · best test ${Math.round(material.stats.bestScore * 100)}%`}
              </p>
            </div>
          </Link>
        );
      })}
    </div>
  );
}
