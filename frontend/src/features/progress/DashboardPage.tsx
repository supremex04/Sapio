import { ArrowRight, BookOpen, Brain, Clock, Flame, Layers, PlayCircle, Repeat, Sparkles, Target, Trophy, Upload } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { firstName, formatRelative, greeting } from "../../shared/lib/format";
import { Badge } from "../../shared/ui/Badge";
import { ButtonLink } from "../../shared/ui/Button";
import { Card, CardHeader } from "../../shared/ui/Card";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { ProgressBar, ProgressRing } from "../../shared/ui/Progress";
import { Skeleton } from "../../shared/ui/Skeleton";
import { useAuth } from "../auth/AuthContext";
import { documentsApi } from "../documents/api";
import { displayTitle, type DocumentDto } from "../documents/types";
import { studyApi } from "../study/api";
import { nextStepCta, nextStepHref, reviewMinutes } from "../study/nextStep";
import type { Material, NextStep, NextStepKind, ReviewSummary } from "../study/types";
import { ActivityChart } from "./ActivityChart";
import { progressApi } from "./api";
import { goalProgress, levelProgress, streakMessage } from "./levels";
import { useProgress } from "./ProgressContext";
import type { ActivityItem, DailyActivity } from "./types";
import "./progress.css";

interface ContinueItem {
  doc: DocumentDto;
  material: Material | null;
}

export function DashboardPage() {
  const { user } = useAuth();
  const { summary } = useProgress();
  const [nextStep, setNextStep] = useState<NextStep | null | undefined>(undefined);
  const [reviews, setReviews] = useState<ReviewSummary | null>(null);
  const [days, setDays] = useState<DailyActivity[] | null>(null);
  const [activity, setActivity] = useState<ActivityItem[] | null>(null);
  const [continueItems, setContinueItems] = useState<ContinueItem[] | null>(null);

  useEffect(() => {
    studyApi.nextStep().then(setNextStep, () => setNextStep(null));
    studyApi.reviewSummary().then(setReviews, () => setReviews(null));
    progressApi.daily(14).then((r) => setDays(r.days), () => setDays([]));
    progressApi.activity(8).then((r) => setActivity(r.items), () => setActivity([]));
    documentsApi
      .list(0, 12)
      .then(async (page) => {
        const ready = page.items.filter((d) => d.status === "READY").slice(0, 3);
        const materials = await Promise.all(ready.map((d) => studyApi.material(d.id).catch(() => null)));
        setContinueItems(ready.map((doc, i) => ({ doc, material: materials[i] ?? null })));
      })
      .catch(() => setContinueItems([]));
  }, []);

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="dash-header">
          <p className="eyebrow">{new Intl.DateTimeFormat(undefined, { weekday: "long", month: "long", day: "numeric" }).format(new Date())}</p>
          <h1>
            {greeting()}
            {user ? `, ${firstName(user.fullName)}` : ""}
          </h1>
          {summary && <p className="muted">{streakMessage(summary.streak)}</p>}
        </header>

        <NextStepCard step={nextStep} />

        <section className="stat-grid" aria-label="Today">
          <GoalCard />
          <Card className="stat-card">
            <div className="stat-icon streak-icon">
              <Flame size={20} aria-hidden />
            </div>
            <div>
              <p className="stat-label">Streak</p>
              <p className="stat-value">{summary ? `${summary.streak.current} day${summary.streak.current === 1 ? "" : "s"}` : <Skeleton width={60} height={24} />}</p>
              <p className="xs muted">Longest: {summary?.streak.longest ?? 0}</p>
            </div>
          </Card>
          <Card className="stat-card">
            <div className="stat-icon level-icon">
              <Trophy size={20} aria-hidden />
            </div>
            <div style={{ flex: 1, minWidth: 0 }}>
              <p className="stat-label">Level {summary?.xp.level ?? 1}</p>
              <p className="stat-value">{summary ? `${summary.xp.total} XP` : <Skeleton width={70} height={24} />}</p>
              {summary && <ProgressBar size="sm" value={levelProgress(summary.xp)} label="Progress to next level" />}
            </div>
          </Card>
          <Card className="stat-card">
            <div className="stat-icon review-icon">
              <Repeat size={20} aria-hidden />
            </div>
            <div style={{ flex: 1 }}>
              <p className="stat-label">Reviews</p>
              <p className="stat-value">{reviews ? `${reviews.dueNow} due` : <Skeleton width={60} height={24} />}</p>
              {reviews && reviews.dueNow > 0 ? (
                <Link to="/review" className="xs">
                  Review now · ~{reviewMinutes(reviews.dueNow)} min →
                </Link>
              ) : (
                <p className="xs muted">{reviews?.total ? "All caught up" : "Answer questions to build your deck"}</p>
              )}
            </div>
          </Card>
        </section>

        <div className="dash-grid">
          <Card>{days ? <ActivityChart days={days} goalMinutes={summary?.goal.dailyMinutes ?? null} /> : <Skeleton height={240} />}</Card>
          <Card>
            <CardHeader icon={<Clock size={18} />} title="Recent activity" />
            <ActivityFeed items={activity} />
          </Card>
        </div>

        <section className="stack">
          <div className="row-between">
            <h2>Continue learning</h2>
            <Link to="/library" className="small">
              Library →
            </Link>
          </div>
          <ContinueLearning items={continueItems} />
        </section>
      </div>
    </PageTransition>
  );
}

const STEP_ICONS: Record<NextStepKind, typeof Repeat> = { REVIEW: Repeat, CONTINUE_TOPIC: PlayCircle, START_TOPIC: BookOpen, TAKE_TEST: Brain, UPLOAD: Upload };

/** One recommended action (avoids choice overload; learning-science.md §2). */
const FALLBACK_STEP: NextStep = {
  kind: "UPLOAD",
  documentId: null,
  topicId: null,
  title: "Open your library",
  reason: "Pick up any study guide, or add new material to learn from.",
  estimatedMinutes: null,
};

function NextStepCard({ step: loaded }: { step: NextStep | null | undefined }) {
  if (loaded === undefined) return <Skeleton height={148} radius="var(--radius-xl)" />;
  const step = loaded ?? FALLBACK_STEP;
  const Icon = STEP_ICONS[step.kind];
  return (
    <motion.div initial={{ opacity: 0, y: 10 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.4, ease: [0.22, 1, 0.36, 1] }}>
      <Link to={nextStepHref(step)} className="next-step" aria-label={`${nextStepCta(step)}: ${step.title}`}>
        <div className="next-step-icon" aria-hidden>
          <Icon size={28} />
        </div>
        <div className="next-step-body">
          <p className="next-step-eyebrow">Your next best step</p>
          <h2>{step.title}</h2>
          <p>{step.reason}</p>
        </div>
        <div className="next-step-cta">
          {step.estimatedMinutes != null && <span className="next-step-time">~{step.estimatedMinutes} min</span>}
          <span className="btn btn-lg next-step-btn">
            {nextStepCta(step)} <ArrowRight size={18} aria-hidden />
          </span>
        </div>
      </Link>
    </motion.div>
  );
}

/** Daily goal ring: goal-gradient effect, effort grows as the ring fills. */
function GoalCard() {
  const { summary } = useProgress();
  if (!summary) {
    return (
      <Card className="stat-card">
        <Skeleton width={64} height={64} radius="50%" />
      </Card>
    );
  }
  const { goal } = summary;
  const left = Math.max(0, goal.dailyMinutes - goal.todayMinutes);
  return (
    <Card className="stat-card">
      <ProgressRing value={goalProgress(goal)} size={64} stroke={7} label="Daily goal" color={goal.reached ? "var(--color-success)" : undefined}>
        <Target size={18} aria-hidden style={{ color: goal.reached ? "var(--color-success)" : "var(--color-primary)" }} />
      </ProgressRing>
      <div>
        <p className="stat-label">Daily goal</p>
        <p className="stat-value">
          {Math.round(goal.todayMinutes)}
          <span className="stat-unit"> / {goal.dailyMinutes} min</span>
        </p>
        <p className="xs muted">{goal.reached ? "Done for today. Anything more is a bonus." : `${Math.ceil(left)} min to go`}</p>
      </div>
    </Card>
  );
}

const ACTIVITY_LABELS: Record<string, string> = {
  LESSON_STARTED: "Started a lesson",
  LESSON_COMPLETED: "Completed a topic",
  TOPIC_COMPLETED: "Completed a topic",
  CHECK_ANSWERED: "Answered a check",
  ASSESSMENT_ANSWERED: "Answered a test question",
  ASSESSMENT_STARTED: "Started a test",
  ASSESSMENT_COMPLETED: "Finished a test",
  REVIEW_ANSWERED: "Reviewed a card",
  TUTOR_QUESTION: "Asked the tutor",
  DAILY_GOAL_REACHED: "Reached the daily goal",
};

function activityLabel(type: string): string {
  return ACTIVITY_LABELS[type] ?? type.toLowerCase().replace(/_/g, " ").replace(/^./, (c) => c.toUpperCase());
}

function ActivityFeed({ items }: { items: ActivityItem[] | null }) {
  if (!items) {
    return (
      <div className="stack-sm">
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} height={36} />
        ))}
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <EmptyState icon={<Sparkles size={24} />} title="Nothing yet">
        Your learning moments will show up here.
      </EmptyState>
    );
  }
  return (
    <ul className="feed">
      {items.map((item, i) => (
        <li key={`${item.occurredAt}-${i}`}>
          <span className="feed-dot" aria-hidden />
          <div className="feed-body">
            <p className="small">
              <strong>{activityLabel(item.type)}</strong>
              {item.topicTitle && <span className="text-2"> · {item.topicTitle}</span>}
            </p>
            <p className="xs muted">
              {item.documentTitle && `${item.documentTitle} · `}
              {formatRelative(item.occurredAt)}
            </p>
          </div>
          {item.xp > 0 && <span className="feed-xp">+{item.xp}</span>}
        </li>
      ))}
    </ul>
  );
}

function ContinueLearning({ items }: { items: ContinueItem[] | null }) {
  if (!items) {
    return (
      <div className="continue-grid">
        {[0, 1, 2].map((i) => (
          <Skeleton key={i} height={150} radius="var(--radius-lg)" />
        ))}
      </div>
    );
  }
  if (items.length === 0) {
    return (
      <Card>
        <EmptyState
          icon={<Upload size={24} />}
          title="Bring your first document"
          action={
            <ButtonLink to="/library" variant="primary" icon={<Upload size={16} />}>
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
        return (
          <Link key={doc.id} to={`/documents/${doc.id}`} className="card card-interactive continue-card">
            <div className="row-between">
              <span className="doc-glyph" aria-hidden>
                <BookOpen size={18} />
              </span>
              {material && material.stats.reviewsDue > 0 && (
                <Badge tone="accent" icon={<Layers />}>
                  {material.stats.reviewsDue} to review
                </Badge>
              )}
            </div>
            <h3 className="break">{material?.title ?? displayTitle(doc)}</h3>
            <div className="stack-sm" style={{ marginTop: "auto" }}>
              <ProgressBar size="sm" value={total ? done / total : 0} label="Topics completed" />
              <p className="xs muted">
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

