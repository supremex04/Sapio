import { Flag } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { useParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { celebrate } from "../../../shared/lib/celebrate";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { ConfirmButton } from "../../../shared/ui/ConfirmButton";
import { cx } from "../../../shared/ui/cx";
import { FocusHeader } from "../../../shared/ui/FocusHeader";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import { GeneratingState } from "../components/GeneratingState";
import { QuestionCard } from "../components/QuestionCard";
import type { AnswerResult, Assessment } from "../types";
import { AssessmentReportView } from "./AssessmentReport";
import "./assessment.css";

/** Test runner (focus mode): one question at a time, resumes at the first unanswered question. */
export function AssessmentPage() {
  const { id = "" } = useParams();
  const { refresh } = useProgress();
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [finishing, setFinishing] = useState(false);
  const [justFinished, setJustFinished] = useState(false);

  useEffect(() => {
    studyApi.assessment(id).then(
      (a) => {
        setAssessment(a);
        const firstOpen = a.items.findIndex((it) => !it.answer);
        setIndex(firstOpen === -1 ? Math.max(0, a.items.length - 1) : firstOpen);
      },
      (e) => setError(errorMessage(e)),
    );
  }, [id]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [index]);

  async function finish() {
    setFinishing(true);
    try {
      const done = await studyApi.completeAssessment(id);
      setAssessment(done);
      setJustFinished(true);
      if ((done.report?.score ?? 0) >= 0.8) celebrate("big");
      void refresh();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setFinishing(false);
    }
  }

  if (error && !assessment) return <Alert>{error}</Alert>;
  if (!assessment) {
    return (
      <div className="stack" style={{ paddingTop: "var(--space-8)" }}>
        <Skeleton height={20} />
        <Skeleton height={320} radius="var(--radius-xl)" />
      </div>
    );
  }
  if (finishing) return <GeneratingState title="Building your report" subtitle="Looking at what you know well and what to strengthen next." />;
  if (assessment.status === "COMPLETED" && assessment.report) return <AssessmentReportView assessment={assessment} fresh={justFinished} />;

  const items = assessment.items;
  const item = items[index];
  const answered = items.filter((it) => it.answer).length;
  const allAnswered = answered === items.length;

  function recordAnswer(position: number, result: AnswerResult) {
    setAssessment((a) => (a ? { ...a, answeredCount: a.answeredCount + 1, items: a.items.map((it) => (it.position === position ? { ...it, answer: result } : it)) } : a));
    void refresh();
  }

  function goNext() {
    const nextOpen = items.findIndex((it, i) => i > index && !it.answer);
    const firstOpen = items.findIndex((it) => !it.answer);
    setIndex(nextOpen !== -1 ? nextOpen : firstOpen !== -1 ? firstOpen : Math.min(index + 1, items.length - 1));
  }

  return (
    <>
      <FocusHeader
        exitTo={`/documents/${assessment.documentId}`}
        exitLabel="Leave test (progress is saved)"
        progress={answered / items.length}
        progressLabel="Questions answered"
        right={
          <>
            <span className="ar-counter tabular">
              {answered}/{items.length}
            </span>
            <ConfirmButton onConfirm={finish} icon={<Flag size={16} />} prompt={`Finish with ${items.length - answered} unanswered?`} confirmLabel="Finish">
              <span className="ar-finish-label">Finish</span>
            </ConfirmButton>
          </>
        }
      />

      {item && (
        <AnimatePresence mode="wait">
          <motion.div key={item.position} className="ar-question" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}>
            <div className="ar-meta">
              <span className="eyebrow">
                Question {item.position} of {items.length}
              </span>
              <Badge tone="mastery">{item.question.topicTitle}</Badge>
            </div>
            <QuestionCard
              key={item.position}
              question={item.question}
              initialResult={item.answer}
              onSubmit={(input) => studyApi.answerItem(assessment.id, item.position, input)}
              onAnswered={(r) => recordAnswer(item.position, r)}
              onContinue={allAnswered ? finish : goNext}
              continueLabel={allAnswered ? "See my results" : "Continue"}
            />
          </motion.div>
        </AnimatePresence>
      )}

      <nav className="ar-dots" aria-label="Questions">
        {items.map((it, i) => (
          <button
            key={it.position}
            type="button"
            className={cx("ar-dot", i === index && "is-current", it.answer && `is-${it.answer.verdict.toLowerCase()}`)}
            aria-label={`Question ${it.position}${it.answer ? ` (answered: ${it.answer.verdict.toLowerCase()})` : ""}`}
            aria-current={i === index ? "step" : undefined}
            onClick={() => setIndex(i)}
          />
        ))}
      </nav>
      {error && <Alert>{error}</Alert>}
    </>
  );
}
