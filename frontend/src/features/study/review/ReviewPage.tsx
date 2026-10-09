import { ArrowRight, CalendarCheck, Layers, PartyPopper } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { celebrate } from "../../../shared/lib/celebrate";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { Button, ButtonLink } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { ProgressBar } from "../../../shared/ui/Progress";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import { QuestionCard } from "../components/QuestionCard";
import { reviewMinutes } from "../nextStep";
import type { AnswerResult, ReviewItem, ReviewSummary } from "../types";
import "../study.css";

const SESSION_SIZE = 10;

/** Spaced-retrieval session: short, one card at a time, ends on a positive summary. */
export function ReviewPage() {
  const [params] = useSearchParams();
  const documentId = params.get("documentId") ?? undefined;
  const { refresh } = useProgress();
  const [items, setItems] = useState<ReviewItem[] | null>(null);
  const [summary, setSummary] = useState<ReviewSummary | null>(null);
  const [index, setIndex] = useState(0);
  const [results, setResults] = useState<Record<string, AnswerResult>>({});
  const [error, setError] = useState<string | null>(null);
  const [session, setSession] = useState(0);

  useEffect(() => {
    setItems(null);
    setIndex(0);
    setResults({});
    studyApi.dueReviews(SESSION_SIZE, documentId).then((r) => setItems(r.items), (e) => setError(errorMessage(e)));
    studyApi.reviewSummary().then(setSummary, () => undefined);
  }, [documentId, session]);

  const done = items !== null && items.length > 0 && index >= items.length;
  useEffect(() => {
    if (done) celebrate("small");
  }, [done]);

  if (error) return <Alert>{error}</Alert>;
  if (!items) return <Skeleton height={320} radius="var(--radius-xl)" />;

  if (items.length === 0) {
    return (
      <PageTransition>
        <Card className="review-empty">
          <EmptyState
            icon={<CalendarCheck size={26} />}
            title="You're all caught up"
            action={
              <ButtonLink to="/" variant="primary" iconRight={<ArrowRight size={16} />}>
                Back home
              </ButtonLink>
            }
          >
            {summary && summary.total > 0
              ? `${summary.total} cards in your deck. ${summary.dueToday > 0 ? `${summary.dueToday} more come due later today.` : "The next ones come due soon."} Reviewing right when you're about to forget is what makes memories last.`
              : "Answer lesson checks and test questions; each one becomes a review card that comes back right before you'd forget it."}
          </EmptyState>
        </Card>
      </PageTransition>
    );
  }

  if (done) {
    const all = Object.values(results);
    const correct = all.filter((r) => r.verdict === "CORRECT").length;
    return (
      <PageTransition>
        <Card className="complete-card">
          <div className="complete-icon" aria-hidden>
            <PartyPopper size={28} />
          </div>
          <h2>Review session done</h2>
          <p className="muted">
            You recalled {correct} of {all.length} on the spot. Each one now comes back a little later, and the ones that slipped come back sooner.
          </p>
          <div className="row" style={{ justifyContent: "center" }}>
            <Button variant="primary" onClick={() => setSession((s) => s + 1)} icon={<Layers size={16} />}>
              Check for more
            </Button>
            <ButtonLink to="/" variant="ghost">
              Done for now
            </ButtonLink>
          </div>
        </Card>
      </PageTransition>
    );
  }

  const item = items[index]!;
  const answered = results[item.question.id];

  return (
    <div className="runner">
      <header className="page-header" style={{ marginBottom: "var(--space-2)" }}>
        <div>
          <p className="eyebrow">Spaced review</p>
          <h1>Quick recall</h1>
          <p>
            {items.length} card{items.length === 1 ? "" : "s"} · about {reviewMinutes(items.length)} min. Pulling answers from memory is what makes them stick.
          </p>
        </div>
      </header>
      <div className="runner-progress">
        <ProgressBar value={index / items.length} label="Review progress" />
        <span className="small muted tabular nowrap">
          {index + 1}/{items.length}
        </span>
      </div>
      <AnimatePresence mode="wait">
        <motion.div key={item.question.id} initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.25 }}>
          <Card className="runner-card">
            <div className="row-between" style={{ marginBottom: "var(--space-4)" }}>
              <span className="small muted break">{item.documentTitle}</span>
              <Badge>{item.question.topicTitle}</Badge>
            </div>
            <QuestionCard
              key={item.question.id}
              question={item.question}
              onSubmit={(input) => studyApi.answerReview(item.question.id, input)}
              onAnswered={(r) => {
                setResults((all) => ({ ...all, [item.question.id]: r }));
                void refresh();
              }}
            />
            {answered && (
              <div className="runner-next">
                <Button variant="primary" size="lg" onClick={() => setIndex((i) => i + 1)} iconRight={<ArrowRight size={18} />}>
                  {index === items.length - 1 ? "Finish" : "Next card"}
                </Button>
              </div>
            )}
          </Card>
        </motion.div>
      </AnimatePresence>
    </div>
  );
}
