import { ArrowDown, ArrowLeft, ArrowRight, BookOpenCheck, Brain, CheckCircle2, FlaskConical, Lightbulb, ListChecks, MessageSquareQuote, Sparkles, Trophy } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router";
import { ApiError, errorMessage } from "../../../shared/api/errors";
import { celebrate } from "../../../shared/lib/celebrate";
import { Alert } from "../../../shared/ui/Alert";
import { Button, ButtonLink } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { Markdown } from "../../../shared/ui/Markdown";
import { ProgressBar } from "../../../shared/ui/Progress";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import { GeneratingState } from "../components/GeneratingState";
import { QuestionCard } from "../components/QuestionCard";
import { buildLessonSteps, initialRevealCount, nextTopicAfter, type LessonStep } from "../path";
import type { AnswerResult, Lesson, LessonCheck, Material, SectionKind } from "../types";
import { TutorPanel } from "./TutorPanel";
import "../study.css";

const KIND_META: Record<SectionKind, { label: string; icon: typeof Lightbulb }> = {
  CONCEPT: { label: "Concept", icon: Lightbulb },
  EXAMPLE: { label: "Worked example", icon: FlaskConical },
  ANALOGY: { label: "Think of it like", icon: MessageSquareQuote },
  SUMMARY: { label: "Pulling it together", icon: BookOpenCheck },
};

export function LessonPage() {
  const { id: documentId = "", topicId = "" } = useParams();
  const { refresh } = useProgress();
  const [material, setMaterial] = useState<Material | null>(null);
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);

  useEffect(() => {
    studyApi.material(documentId).then(setMaterial, () => undefined);
  }, [documentId]);

  useEffect(() => {
    setLesson(null);
    setError(null);
    studyApi.lesson(topicId).then(setLesson, (e) => setError(e instanceof ApiError && e.code === "AI_UNAVAILABLE" ? "The tutor is busy right now. Give it a moment and try again." : errorMessage(e)));
  }, [topicId, attempt]);

  const topic = material?.topics.find((t) => t.id === topicId);

  return (
    <div className="lesson-page">
      <Link to={`/documents/${documentId}/learn`} className="back-link">
        <ArrowLeft size={16} aria-hidden /> Learning path
      </Link>
      {error ? (
        <Alert action={<Button size="sm" variant="soft" onClick={() => setAttempt((n) => n + 1)}>Try again</Button>}>{error}</Alert>
      ) : !lesson ? (
        <GeneratingState title={topic ? `Preparing "${topic.title}"` : "Preparing your lesson"} subtitle="Your tutor is reading the source and writing a lesson just for this topic." />
      ) : (
        <LessonFlow key={lesson.topicId} lesson={lesson} documentId={documentId} material={material} onProgress={refresh} />
      )}
      {lesson && <TutorPanel topicId={topicId} topicTitle={lesson.title} />}
    </div>
  );
}

function LessonFlow({ lesson, documentId, material, onProgress }: { lesson: Lesson; documentId: string; material: Material | null; onProgress: () => Promise<void> }) {
  const steps = useMemo(() => buildLessonSteps(lesson), [lesson]);
  const [revealed, setRevealed] = useState(() => initialRevealCount(steps, lesson.status === "COMPLETED"));
  const [answers, setAnswers] = useState<Record<string, AnswerResult>>(() =>
    Object.fromEntries(lesson.checks.filter((c) => c.lastAnswer).map((c) => [c.id, c.lastAnswer as AnswerResult])),
  );
  const [completed, setCompleted] = useState(lesson.status === "COMPLETED");
  const [justCompleted, setJustCompleted] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [completeError, setCompleteError] = useState<string | null>(null);
  const endRef = useRef<HTMLDivElement>(null);
  const completeRef = useRef<HTMLDivElement>(null);
  const firstRender = useRef(true);

  useEffect(() => {
    if (justCompleted) setTimeout(() => completeRef.current?.scrollIntoView({ behavior: "smooth", block: "center" }), 150);
  }, [justCompleted]);

  useEffect(() => {
    if (firstRender.current) {
      firstRender.current = false;
      return;
    }
    endRef.current?.scrollIntoView({ behavior: "smooth", block: "end" });
  }, [revealed]);

  const current = steps[revealed - 1];
  const blocked = !!current && (current.kind === "check" || current.kind === "warmup") && !answers[current.check.id];
  const sectionCount = lesson.sections.length;
  const sectionsSeen = steps.slice(0, revealed).filter((s) => s.kind === "section").length;

  const onAnswered = useCallback(
    (check: LessonCheck, result: AnswerResult) => {
      setAnswers((a) => ({ ...a, [check.id]: result }));
      void onProgress();
    },
    [onProgress],
  );

  async function complete() {
    setCompleting(true);
    setCompleteError(null);
    try {
      await studyApi.completeTopic(lesson.topicId);
      setCompleted(true);
      setJustCompleted(true);
      celebrate("small");
      void onProgress();
    } catch (e) {
      setCompleteError(errorMessage(e));
    } finally {
      setCompleting(false);
    }
  }

  const next = material ? nextTopicAfter(material.topics, lesson.topicId) : undefined;

  return (
    <div className="lesson">
      <header className="lesson-header">
        <p className="eyebrow">Lesson{material ? ` · ${material.title}` : ""}</p>
        <h1>{lesson.title}</h1>
        <div className="lesson-progress">
          <ProgressBar value={completed ? 1 : sectionCount ? sectionsSeen / (sectionCount + 1) : 0} label="Lesson progress" size="sm" />
          <span className="xs muted nowrap">{completed ? "Completed" : `${sectionsSeen} of ${sectionCount} parts`}</span>
        </div>
      </header>

      <div className="lesson-steps">
        <AnimatePresence initial={false}>
          {steps.slice(0, revealed).map((step, i) => (
            <motion.div key={stepKey(step, i)} initial={{ opacity: 0, y: 16 }} animate={{ opacity: 1, y: 0 }} transition={{ duration: 0.35, ease: [0.22, 1, 0.36, 1] }}>
              <StepView
                step={step}
                answer={step.kind === "check" || step.kind === "warmup" ? answers[step.check.id] ?? null : null}
                onAnswered={onAnswered}
                lesson={lesson}
                completed={completed}
                completing={completing}
                completeError={completeError}
                onComplete={complete}
              />
            </motion.div>
          ))}
        </AnimatePresence>
      </div>

      {revealed < steps.length && (
        <div className="lesson-continue">
          <Button variant="primary" size="lg" onClick={() => setRevealed((r) => r + 1)} disabled={blocked} iconRight={<ArrowDown size={18} />}>
            {current?.kind === "warmup" ? "Start the lesson" : "Continue"}
          </Button>
          {blocked && <p className="xs muted">Answer the question to continue. A guess is fine.</p>}
        </div>
      )}

      <AnimatePresence>
        {completed && revealed >= steps.length && (
          <motion.div ref={completeRef} initial={{ opacity: 0, scale: 0.97 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: "spring", stiffness: 300, damping: 26 }}>
            <Card className="complete-card">
              <div className="complete-icon" aria-hidden>
                <Trophy size={28} />
              </div>
              <h2>{justCompleted ? "Topic complete. Nicely done!" : "You've completed this topic"}</h2>
              <p className="muted">
                {justCompleted ? "You just explained, practised and checked this idea. That's how knowledge sticks." : "Revisit anytime; the checks above feed your spaced reviews."}
              </p>
              <div className="row" style={{ justifyContent: "center" }}>
                {next ? (
                  <ButtonLink to={`/documents/${documentId}/learn/${next.id}`} variant="primary" iconRight={<ArrowRight size={16} />}>
                    Next: {next.title}
                  </ButtonLink>
                ) : (
                  <ButtonLink to={`/documents/${documentId}/test`} variant="primary" icon={<Brain size={16} />}>
                    Test my knowledge
                  </ButtonLink>
                )}
                <ButtonLink to={`/documents/${documentId}/learn`} variant="ghost">
                  Back to path
                </ButtonLink>
              </div>
            </Card>
          </motion.div>
        )}
      </AnimatePresence>
      <div ref={endRef} />
    </div>
  );
}

function stepKey(step: LessonStep, i: number): string {
  if (step.kind === "section") return `s${step.section.index}`;
  if (step.kind === "finish") return "finish";
  return `c${step.check.id}-${i}`;
}

interface StepViewProps {
  step: LessonStep;
  answer: AnswerResult | null;
  onAnswered: (check: LessonCheck, result: AnswerResult) => void;
  lesson: Lesson;
  completed: boolean;
  completing: boolean;
  completeError: string | null;
  onComplete: () => void;
}

function StepView({ step, answer, onAnswered, lesson, completed, completing, completeError, onComplete }: StepViewProps) {
  if (step.kind === "section") {
    const { label, icon: Icon } = KIND_META[step.section.kind] ?? KIND_META.CONCEPT;
    return (
      <article className={`section-card kind-${step.section.kind.toLowerCase()}`}>
        <p className="section-kind">
          <Icon size={15} aria-hidden /> {label}
        </p>
        {step.section.heading && <h2>{step.section.heading}</h2>}
        <Markdown>{step.section.body}</Markdown>
      </article>
    );
  }
  if (step.kind === "warmup" || step.kind === "check") {
    const { check } = step;
    const warmup = step.kind === "warmup";
    const explain = check.type === "SHORT_ANSWER";
    return (
      <Card className={`check-card ${warmup ? "check-warmup" : ""} ${explain ? "check-explain" : ""}`}>
        <QuestionCard
          question={check}
          initialResult={answer}
          allowRetry={!warmup}
          context={warmup ? "warmup" : "normal"}
          eyebrow={
            warmup ? (
              <>
                <Sparkles size={14} aria-hidden /> Warm-up
              </>
            ) : explain ? (
              <>
                <MessageSquareQuote size={14} aria-hidden /> Explain it back
              </>
            ) : (
              <>
                <ListChecks size={14} aria-hidden /> Quick check
              </>
            )
          }
          hint={
            warmup
              ? "Make a guess. Guessing first helps you remember, even if you're wrong."
              : explain
                ? "Put it in your own words, as if teaching a friend. Explaining is one of the best ways to understand."
                : undefined
          }
          onSubmit={(input) => studyApi.answerQuestion(check.id, input)}
          onAnswered={(r) => onAnswered(check, r)}
        />
      </Card>
    );
  }
  return (
    <Card className="takeaways-card">
      <p className="section-kind">
        <CheckCircle2 size={15} aria-hidden /> Key takeaways
      </p>
      <ul className="takeaways">
        {lesson.keyTakeaways.map((t, i) => (
          <motion.li key={i} initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.08 * i }}>
            <Markdown>{t}</Markdown>
          </motion.li>
        ))}
      </ul>
      {!completed && (
        <div className="stack-sm" style={{ alignItems: "center", marginTop: "var(--space-4)" }}>
          <Button variant="primary" size="lg" loading={completing} onClick={onComplete} icon={<Trophy size={18} />}>
            Complete topic
          </Button>
          {completeError && <Alert>{completeError}</Alert>}
        </div>
      )}
    </Card>
  );
}
