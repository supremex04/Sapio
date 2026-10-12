import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { useSearchParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { Dia } from "../../../shared/illustrations/Dia";
import { celebrate } from "../../../shared/lib/celebrate";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { Button, ButtonLink } from "../../../shared/ui/Button";
import { Chip } from "../../../shared/ui/Chip";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { FocusHeader } from "../../../shared/ui/FocusHeader";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import { QuestionCard } from "../components/QuestionCard";
import { ResultStat } from "../components/ResultStat";
import { reviewMinutes } from "../nextStep";
import type { AnswerResult, ReviewItem, ReviewSummary } from "../types";
import "./recall.css";

const SESSION_SIZE = 10;

/** Spaced-retrieval session (focus mode): short, one card at a time, ends on a positive summary. */
export function RecallPage() {
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
    studyApi.dueReviews(SESSION_SIZE, documentId).then(
      (r) => setItems(r.items),
      (e) => setError(errorMessage(e)),
    );
    studyApi.reviewSummary().then(setSummary, () => undefined);
  }, [documentId, session]);

  const done = items !== null && items.length > 0 && index >= items.length;
  useEffect(() => {
    if (done) celebrate("small");
  }, [done]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [index]);

  const exitTo = documentId ? `/documents/${documentId}` : "/";
  const total = items?.length ?? 0;
  const header = (progress: number, label: string) => (
    <FocusHeader
      exitTo={exitTo}
      exitLabel="Exit recall"
      progress={progress}
      progressLabel={label}
      right={
        items && items.length > 0 && !done ? (
          <Chip emoji="recall" tone="review" label={`${total - index} cards left`}>
            {total - index}
          </Chip>
        ) : undefined
      }
    />
  );

  if (error) {
    return (
      <>
        {header(0, "Recall")}
        <Alert>{error}</Alert>
      </>
    );
  }
  if (!items) {
    return (
      <>
        {header(0, "Loading recall")}
        <Skeleton height={320} radius="var(--radius-xl)" />
      </>
    );
  }

  if (items.length === 0) {
    return (
      <>
        {header(1, "Nothing due")}
        <div className="rv-center">
          <EmptyState
            mood="sleepy"
            title="Nothing due, your memory is fresh"
            action={
              <ButtonLink to="/" variant="primary" size="lg">
                Back home
              </ButtonLink>
            }
          >
            {summary && summary.total > 0
              ? `${summary.total} cards in your deck. ${summary.dueToday > 0 ? `${summary.dueToday} more come due later today.` : "The next ones come due soon."} Recalling right when you're about to forget is what makes memories last.`
              : "Answer lesson checks and test questions; each one becomes a recall card that comes back right before you'd forget it."}
          </EmptyState>
        </div>
      </>
    );
  }

  if (done) {
    const all = Object.values(results);
    const recalled = all.filter((r) => r.verdict === "CORRECT").length;
    return (
      <>
        {header(1, "Recall complete")}
        <motion.div className="rv-done" initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: "spring", stiffness: 300, damping: 24 }}>
          <Dia mood="cheer" size={140} />
          <div className="stack-sm">
            <h1>Recall complete!</h1>
            <p className="text-2">Each card you recalled now comes back a little later. The ones that slipped come back sooner, and that's how memories get stronger.</p>
          </div>
          <div className="result-stats">
            <ResultStat tone="green" label="Recalled" emoji="brain" value={recalled} />
            <ResultStat tone="teal" label="Back soon" emoji="recall" value={all.length - recalled} />
            <ResultStat tone="blue" label="Cards" emoji="card-index-dividers" value={all.length} />
          </div>
          <div className="stack-sm" style={{ width: "100%", alignItems: "center" }}>
            <Button variant="review" size="lg" onClick={() => setSession((s) => s + 1)}>
              Check for more
            </Button>
            <ButtonLink to={exitTo} variant="ghost">
              Done for now
            </ButtonLink>
          </div>
        </motion.div>
      </>
    );
  }

  const item = items[index]!;
  const isLast = index === items.length - 1;

  return (
    <>
      {header(index / items.length, "Recall progress")}
      <AnimatePresence mode="wait">
        <motion.div key={item.question.id} className="rv-card" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.22 }}>
          <div className="rv-meta">
            <span className="eyebrow">
              Quick recall · ~{reviewMinutes(items.length)} min
            </span>
            <div className="row">
              <Badge tone="review">{item.question.topicTitle}</Badge>
              <span className="xs muted break">{item.documentTitle}</span>
            </div>
          </div>
          <QuestionCard
            key={item.question.id}
            question={item.question}
            onSubmit={(input) => studyApi.answerReview(item.question.id, input)}
            onAnswered={(r) => {
              setResults((all) => ({ ...all, [item.question.id]: r }));
              void refresh();
            }}
            onContinue={() => setIndex((i) => i + 1)}
            continueLabel={isLast ? "Finish" : "Continue"}
          />
        </motion.div>
      </AnimatePresence>
    </>
  );
}
