import { ArrowLeft, ArrowRight, Flag } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { celebrate } from "../../../shared/lib/celebrate";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { Button } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { ConfirmButton } from "../../../shared/ui/ConfirmButton";
import { ProgressBar } from "../../../shared/ui/Progress";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import { GeneratingState } from "../components/GeneratingState";
import { QuestionCard } from "../components/QuestionCard";
import type { AnswerResult, Assessment } from "../types";
import { AssessmentReportView } from "./AssessmentReport";
import "../study.css";

export function AssessmentPage() {
  const { id = "" } = useParams();
  const { refresh } = useProgress();
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [finishing, setFinishing] = useState(false);
  const [justFinished, setJustFinished] = useState(false);

  useEffect(() => {
    studyApi.assessment(id).then((a) => {
      setAssessment(a);
      const firstOpen = a.items.findIndex((it) => !it.answer);
      setIndex(firstOpen === -1 ? Math.max(0, a.items.length - 1) : firstOpen);
    }, (e) => setError(errorMessage(e)));
  }, [id]);

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
  if (!assessment) return <Skeleton height={360} radius="var(--radius-xl)" />;
  if (finishing) return <GeneratingState title="Building your report" subtitle="Looking at what you know well and what to strengthen next." />;
  if (assessment.status === "COMPLETED" && assessment.report) return <AssessmentReportView assessment={assessment} fresh={justFinished} />;

  const items = assessment.items;
  const item = items[index];
  const answered = items.filter((it) => it.answer).length;
  const isLast = index === items.length - 1;
  const allAnswered = answered === items.length;

  function recordAnswer(position: number, result: AnswerResult) {
    setAssessment((a) => (a ? { ...a, answeredCount: a.answeredCount + 1, items: a.items.map((it) => (it.position === position ? { ...it, answer: result } : it)) } : a));
    void refresh();
  }

  function goNext() {
    const nextOpen = items.findIndex((it, i) => i > index && !it.answer);
    setIndex(nextOpen === -1 ? Math.min(index + 1, items.length - 1) : nextOpen);
  }

  return (
    <div className="runner">
      <div className="runner-top">
        <Link to={`/documents/${assessment.documentId}`} className="back-link" style={{ marginBottom: 0 }}>
          <ArrowLeft size={16} aria-hidden /> {assessment.documentTitle}
        </Link>
        <ConfirmButton onConfirm={finish} icon={<Flag size={14} />} prompt={`Finish with ${items.length - answered} unanswered?`} confirmLabel="Finish">
          Finish now
        </ConfirmButton>
      </div>
      <div className="runner-progress">
        <ProgressBar value={answered / items.length} label="Questions answered" />
        <span className="small muted tabular nowrap">
          {answered}/{items.length}
        </span>
      </div>

      {item && (
        <AnimatePresence mode="wait">
          <motion.div key={item.position} initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.25, ease: [0.22, 1, 0.36, 1] }}>
            <Card className="runner-card">
              <div className="row-between" style={{ marginBottom: "var(--space-4)" }}>
                <span className="small muted">
                  Question {item.position} of {items.length}
                </span>
                <Badge>{item.question.topicTitle}</Badge>
              </div>
              <QuestionCard
                key={item.position}
                question={item.question}
                initialResult={item.answer}
                onSubmit={(input) => studyApi.answerItem(assessment.id, item.position, input)}
                onAnswered={(r) => recordAnswer(item.position, r)}
              />
              {item.answer && (
                <div className="runner-next">
                  {allAnswered ? (
                    <Button variant="primary" size="lg" onClick={finish} iconRight={<ArrowRight size={18} />}>
                      See my results
                    </Button>
                  ) : (
                    <Button variant="primary" size="lg" onClick={goNext} iconRight={<ArrowRight size={18} />}>
                      {isLast ? "Back to unanswered" : "Next question"}
                    </Button>
                  )}
                </div>
              )}
            </Card>
          </motion.div>
        </AnimatePresence>
      )}

      <nav className="runner-dots" aria-label="Questions">
        {items.map((it, i) => (
          <button
            key={it.position}
            type="button"
            className={`runner-dot ${i === index ? "current" : ""} ${it.answer ? `answered verdict-${it.answer.verdict.toLowerCase()}` : ""}`}
            aria-label={`Question ${it.position}${it.answer ? " (answered)" : ""}`}
            aria-current={i === index ? "step" : undefined}
            onClick={() => setIndex(i)}
          />
        ))}
      </nav>
      {error && <Alert>{error}</Alert>}
    </div>
  );
}
