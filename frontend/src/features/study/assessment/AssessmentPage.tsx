import { Flag } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useRef, useState } from "react";
import { useParams } from "react-router";
import { ApiError, errorMessage } from "../../../shared/api/errors";
import { celebrate } from "../../../shared/lib/celebrate";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { ConfirmButton } from "../../../shared/ui/ConfirmButton";
import { cx } from "../../../shared/ui/cx";
import { FocusHeader } from "../../../shared/ui/FocusHeader";
import { formatDateTime } from "../../../shared/lib/format";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import { GeneratingState } from "../components/GeneratingState";
import type { AnswerInput, Assessment, AssessmentItem } from "../types";
import { AssessmentReportView } from "./AssessmentReport";
import { TestQuestion } from "./TestQuestion";
import "./assessment.css";

/** Test runner (focus mode): one question at a time, resumes at the first unanswered question. */
export function AssessmentPage() {
  const { id = "" } = useParams();
  const { refresh } = useProgress();
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [saving, setSaving] = useState<Set<number>>(new Set());
  const pending = useRef(new Map<number, Promise<void>>());
  const failed = useRef(new Set<number>());
  const [error, setError] = useState<string | null>(null);
  const [index, setIndex] = useState(0);
  const [finishing, setFinishing] = useState(false);
  const [justFinished, setJustFinished] = useState(false);

  useEffect(() => {
    studyApi.assessment(id).then(
      (a) => {
        setAssessment(a);
        const firstOpen = a.items.findIndex((it) => !it.submitted);
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
      await Promise.allSettled(pending.current.values()); // answers still saving in the background
      if (failed.current.size > 0) return; // an answer didn't save: the learner answers it again first
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

  const setItems = (update: (items: AssessmentItem[]) => AssessmentItem[]) =>
    setAssessment((a) => (a ? { ...a, items: update(a.items) } : a));

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
  const answered = items.filter((it) => it.submitted).length;
  const remaining = items.filter((it) => !it.submitted && it.position !== item?.position).length;

  /** Records the answer locally and saves it in the background, so the next question appears at once. */
  function save(position: number, input: AnswerInput) {
    const local = { selectedOptionIndex: input.optionIndex ?? null, answerText: input.text ?? null, confidence: input.confidence ?? null };
    setItems((list) => list.map((it) => (it.position === position ? { ...it, submitted: local } : it)));
    setSaving((s) => new Set(s).add(position));
    const request = studyApi
      .answerItem(id, position, input)
      .then(
        (saved) => {
          failed.current.delete(position);
          setItems((list) => list.map((it) => (it.position === position ? { ...it, submitted: saved } : it)));
        },
        (e) => {
          if (e instanceof ApiError && e.code === "ALREADY_ANSWERED") return; // saved by an earlier attempt
          failed.current.add(position);
          setItems((list) => list.map((it) => (it.position === position ? { ...it, submitted: null } : it)));
          setError(`Question ${position} didn't save (${errorMessage(e)}). Please answer it again.`);
          setIndex(items.findIndex((it) => it.position === position));
        },
      )
      .finally(() => {
        pending.current.delete(position);
        setSaving((s) => {
          const next = new Set(s);
          next.delete(position);
          return next;
        });
      });
    pending.current.set(position, request);
    if (remaining === 0) {
      void finish();
    } else {
      goNext(position);
    }
  }

  function goNext(from: number) {
    const at = items.findIndex((it) => it.position === from);
    const open = (i: number) => !items[i]?.submitted && items[i]?.position !== from;
    const after = items.findIndex((_, i) => i > at && open(i));
    const first = items.findIndex((_, i) => open(i));
    setIndex(after !== -1 ? after : first !== -1 ? first : Math.min(at + 1, items.length - 1));
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

      {error && <Alert>{error}</Alert>}
      {assessment.assignment && (
        <p className="ar-assigned small muted">
          <strong>{assessment.assignment.title ?? "Class test"}</strong> · set by {assessment.assignment.assignedBy}
          {assessment.assignment.dueAt && ` · due ${formatDateTime(assessment.assignment.dueAt)}`}
        </p>
      )}

      {item && (
        <AnimatePresence mode="wait">
          <motion.div key={item.position} className="ar-question" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}>
            <div className="ar-meta">
              <span className="eyebrow">
                Question {item.position} of {items.length}
              </span>
              <Badge tone="mastery">{item.question.topicTitle}</Badge>
            </div>
            <TestQuestion
              key={item.position}
              question={item.question}
              submitted={item.submitted}
              onSave={(input) => save(item.position, input)}
              nextLabel={remaining === 0 ? "Finish test" : "Next"}
            />
          </motion.div>
        </AnimatePresence>
      )}

      <nav className="ar-dots" aria-label="Questions">
        {items.map((it, i) => (
          <button
            key={it.position}
            type="button"
            className={cx("ar-dot", i === index && "is-current", saving.has(it.position) ? "is-saving" : it.submitted && "is-answered")}
            aria-label={`Question ${it.position}${it.submitted ? " (answered)" : ""}`}
            aria-current={i === index ? "step" : undefined}
            onClick={() => setIndex(i)}
          />
        ))}
      </nav>
    </>
  );
}
