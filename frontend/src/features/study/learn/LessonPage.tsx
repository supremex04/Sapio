import { ArrowLeft, ArrowRight } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useCallback, useEffect, useMemo, useRef, useState, type ReactNode } from "react";
import { useParams } from "react-router";
import { ApiError, errorMessage } from "../../../shared/api/errors";
import { Dia } from "../../../shared/illustrations/Dia";
import { Emoji } from "../../../shared/illustrations/Emoji";
import type { EmojiName } from "../../../shared/illustrations/emojiNames";
import { celebrate } from "../../../shared/lib/celebrate";
import { Alert } from "../../../shared/ui/Alert";
import { Button, ButtonLink } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { Chip } from "../../../shared/ui/Chip";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { FeedbackSheet } from "../../../shared/ui/FeedbackSheet";
import { FocusHeader } from "../../../shared/ui/FocusHeader";
import { Markdown } from "../../../shared/ui/Markdown";
import { SpeechBubble } from "../../../shared/ui/SpeechBubble";
import { useProgress } from "../../progress/ProgressContext";
import { studyApi } from "../api";
import { GeneratingState } from "../components/GeneratingState";
import { ResultStat } from "../components/ResultStat";
import { QuestionCard } from "../components/QuestionCard";
import { buildLessonSteps, initialRevealCount, nextTopicAfter, type LessonStep } from "../path";
import type { AnswerResult, Lesson, LessonCheck, Material, SectionKind } from "../types";
import { TutorPanel } from "./TutorPanel";
import "./learn.css";

const KIND_META: Record<SectionKind, { label: string; emoji: EmojiName }> = {
  CONCEPT: { label: "Concept", emoji: "light-bulb" },
  EXAMPLE: { label: "Worked example", emoji: "test-tube" },
  ANALOGY: { label: "Think of it like", emoji: "puzzle-piece" },
  SUMMARY: { label: "Pulling it together", emoji: "memo" },
};

export function LessonPage() {
  const { id: documentId = "", topicId = "" } = useParams();
  const { summary, refresh } = useProgress();
  const [material, setMaterial] = useState<Material | null>(null);
  const [lesson, setLesson] = useState<Lesson | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  const [progress, setProgress] = useState(0);

  useEffect(() => {
    studyApi.material(documentId).then(setMaterial, () => undefined);
  }, [documentId]);

  useEffect(() => {
    setLesson(null);
    setError(null);
    setProgress(0);
    studyApi.lesson(topicId).then(setLesson, (e) => setError(e instanceof ApiError && e.code === "AI_UNAVAILABLE" ? "The tutor is busy right now. Give it a moment and try again." : errorMessage(e)));
  }, [topicId, attempt]);

  const topic = material?.topics.find((t) => t.id === topicId);

  return (
    <>
      <FocusHeader
        exitTo={`/documents/${documentId}/learn`}
        exitLabel="Exit lesson"
        progress={progress}
        progressLabel="Lesson progress"
        right={
          <>
            {lesson && <TutorPanel topicId={topicId} topicTitle={lesson.title} />}
            {summary && (
              <Chip emoji="high-voltage" tone="xp" label={`${summary.xp.total} XP`}>
                {summary.xp.total}
              </Chip>
            )}
          </>
        }
      />
      {error ? (
        <EmptyState
          mood="oops"
          title="Couldn't prepare this lesson"
          action={
            <Button variant="primary" onClick={() => setAttempt((n) => n + 1)}>
              Try again
            </Button>
          }
        >
          {error}
        </EmptyState>
      ) : !lesson ? (
        <GeneratingState title={topic ? `Preparing "${topic.title}"` : "Preparing your lesson"} subtitle="Your tutor is reading the source and writing a lesson just for this topic." />
      ) : (
        <LessonFlow key={lesson.topicId} lesson={lesson} documentId={documentId} material={material} xpTotal={summary?.xp.total ?? null} onProgress={refresh} onStepProgress={setProgress} />
      )}
    </>
  );
}

interface FlowProps {
  lesson: Lesson;
  documentId: string;
  material: Material | null;
  xpTotal: number | null;
  onProgress: () => Promise<void>;
  onStepProgress: (fraction: number) => void;
}

/** One step at a time: warm-up → sections with their checks → takeaways → complete. */
function LessonFlow({ lesson, documentId, material, xpTotal, onProgress, onStepProgress }: FlowProps) {
  const steps = useMemo(() => buildLessonSteps(lesson), [lesson]);
  const alreadyCompleted = lesson.status === "COMPLETED";
  const [index, setIndex] = useState(() => (alreadyCompleted ? 0 : Math.min(steps.length - 1, initialRevealCount(steps, false) - 1)));
  const [answers, setAnswers] = useState<Record<string, AnswerResult>>(() =>
    Object.fromEntries(lesson.checks.filter((c) => c.lastAnswer).map((c) => [c.id, c.lastAnswer as AnswerResult])),
  );
  const [completed, setCompleted] = useState(alreadyCompleted);
  const [done, setDone] = useState(false);
  const [completing, setCompleting] = useState(false);
  const [completeError, setCompleteError] = useState<string | null>(null);
  const startedAt = useRef(Date.now());
  const xpAtStart = useRef<number | null>(xpTotal);
  if (xpAtStart.current === null && xpTotal !== null) xpAtStart.current = xpTotal;

  const step = steps[index];
  const last = steps.length - 1;

  useEffect(() => {
    onStepProgress(done ? 1 : last > 0 ? index / last : 0);
  }, [index, last, done, onStepProgress]);

  useEffect(() => {
    window.scrollTo({ top: 0, behavior: "smooth" });
  }, [index, done]);

  const next = useCallback(() => setIndex((i) => Math.min(i + 1, last)), [last]);

  // Enter continues on reading steps (question steps handle Enter themselves).
  useEffect(() => {
    if (!step || step.kind !== "section" || done) return;
    function onKey(e: KeyboardEvent) {
      const t = e.target;
      if (e.key !== "Enter" || t instanceof HTMLTextAreaElement || t instanceof HTMLInputElement || t instanceof HTMLButtonElement || t instanceof HTMLAnchorElement) return;
      e.preventDefault();
      next();
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [step, done, next]);

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
      setDone(true);
      celebrate("small");
      void onProgress();
    } catch (e) {
      setCompleteError(errorMessage(e));
    } finally {
      setCompleting(false);
    }
  }

  const nextTopic = material ? nextTopicAfter(material.topics, lesson.topicId) : undefined;
  const nextAction = nextTopic ? (
    <ButtonLink to={`/documents/${documentId}/learn/${nextTopic.id}`} variant="primary" size="lg" iconRight={<ArrowRight size={20} />}>
      Next topic
    </ButtonLink>
  ) : (
    <ButtonLink to={`/documents/${documentId}/test`} variant="mastery" size="lg">
      Test yourself
    </ButtonLink>
  );

  if (done) {
    return (
      <LessonComplete
        lesson={lesson}
        answers={answers}
        seconds={Math.round((Date.now() - startedAt.current) / 1000)}
        xpEarned={xpTotal !== null && xpAtStart.current !== null ? Math.max(0, xpTotal - xpAtStart.current) : null}
        nextTopicTitle={nextTopic?.title}
        primary={nextAction}
        backTo={`/documents/${documentId}/learn`}
      />
    );
  }
  if (!step) return null;

  return (
    <div className="ls">
      <div className="ls-title">
        <p className="eyebrow">{material ? material.title : "Lesson"}</p>
        <h1>{lesson.title}</h1>
      </div>
      <AnimatePresence mode="wait" initial={false}>
        <motion.div key={stepKey(step)} className="ls-step" initial={{ opacity: 0, x: 24 }} animate={{ opacity: 1, x: 0 }} exit={{ opacity: 0, x: -24 }} transition={{ duration: 0.22, ease: [0.22, 1, 0.36, 1] }}>
          {step.kind === "section" && (
            <>
              <SectionCard section={step.section} figures={lesson.figures} />
              <div className="ls-sheet-space" aria-hidden />
              <FeedbackSheet
                tone="neutral"
                aside={
                  index > 0 && (
                    <Button variant="ghost" size="sm" icon={<ArrowLeft size={16} />} onClick={() => setIndex((i) => Math.max(0, i - 1))}>
                      Back
                    </Button>
                  )
                }
                actions={
                  <Button variant="primary" size="lg" onClick={next}>
                    Continue
                  </Button>
                }
              />
            </>
          )}

          {(step.kind === "warmup" || step.kind === "check") && (
            <CheckStep step={step} answer={answers[step.check.id] ?? null} onAnswered={onAnswered} onContinue={next} continueLabel={step.kind === "warmup" ? "Start the lesson" : "Continue"} />
          )}

          {step.kind === "finish" && (
            <>
              <Takeaways items={lesson.keyTakeaways} />
              {completeError && <Alert>{completeError}</Alert>}
              <div className="ls-sheet-space" aria-hidden />
              <FeedbackSheet
                tone="neutral"
                aside={
                  completed ? (
                    <ButtonLink to={`/documents/${documentId}/learn`} variant="ghost" size="sm">
                      Back to path
                    </ButtonLink>
                  ) : (
                    index > 0 && (
                      <Button variant="ghost" size="sm" icon={<ArrowLeft size={16} />} onClick={() => setIndex((i) => Math.max(0, i - 1))}>
                        Back
                      </Button>
                    )
                  )
                }
                actions={
                  completed ? (
                    nextAction
                  ) : (
                    <Button variant="success" size="lg" loading={completing} onClick={complete}>
                      Complete topic
                    </Button>
                  )
                }
              />
            </>
          )}
        </motion.div>
      </AnimatePresence>
    </div>
  );
}

function stepKey(step: LessonStep): string {
  if (step.kind === "section") return `s${step.section.index}`;
  if (step.kind === "finish") return "finish";
  return `c${step.check.id}`;
}

function SectionCard({ section, figures }: { section: Lesson["sections"][number]; figures: Lesson["figures"] }) {
  const meta = KIND_META[section.kind] ?? KIND_META.CONCEPT;
  return (
    <article className={`ls-section ls-kind-${(section.kind ?? "CONCEPT").toLowerCase()}`}>
      <div className="ls-section-head">
        <Emoji name={meta.emoji} size={24} />
        <span className="ls-kind-label">{meta.label}</span>
      </div>
      <div className="ls-section-body">
        {section.heading && <h2>{section.heading}</h2>}
        <Markdown figures={figures}>{section.body}</Markdown>
      </div>
    </article>
  );
}

function CheckStep({
  step,
  answer,
  onAnswered,
  onContinue,
  continueLabel,
}: {
  step: Extract<LessonStep, { kind: "warmup" | "check" }>;
  answer: AnswerResult | null;
  onAnswered: (check: LessonCheck, result: AnswerResult) => void;
  onContinue: () => void;
  continueLabel: string;
}) {
  const { check } = step;
  const warmup = step.kind === "warmup";
  const explain = check.type === "SHORT_ANSWER";
  return (
    <>
      {/* Pretesting: a guess before teaching. Dia steps aside once answered (the sheet has Dia's reaction). */}
      {warmup && !answer && (
        <div className="ls-warmup-intro">
          <Dia mood="encourage" size={72} />
          <SpeechBubble>Make a guess, guessing first helps you remember!</SpeechBubble>
        </div>
      )}
      <QuestionCard
        key={check.id}
        question={check}
        initialResult={answer}
        allowRetry={!warmup}
        context={warmup ? "warmup" : "normal"}
        eyebrow={
          warmup ? (
            <>
              <Emoji name="sparkles" size={18} /> Warm-up
            </>
          ) : explain ? (
            <>
              <Emoji name="writing-hand" size={18} /> Explain it back
            </>
          ) : (
            <>
              <Emoji name="bullseye" size={18} /> Quick check
            </>
          )
        }
        hint={explain ? "Put it in your own words, as if teaching a friend. Explaining is one of the best ways to understand." : undefined}
        onSubmit={(input) => studyApi.answerQuestion(check.id, input)}
        onAnswered={(r) => onAnswered(check, r)}
        onContinue={onContinue}
        continueLabel={continueLabel}
      />
    </>
  );
}

function Takeaways({ items }: { items: string[] }) {
  return (
    <Card>
      <div className="card-header">
        <h2 className="card-title">
          <Emoji name="sparkles" size={26} /> Key takeaways
        </h2>
      </div>
      <ul className="ls-takeaways">
        {items.map((t, i) => (
          <motion.li key={i} initial={{ opacity: 0, x: -8 }} animate={{ opacity: 1, x: 0 }} transition={{ delay: 0.08 * i }}>
            <Emoji name="check-mark-button" size={22} />
            <Markdown>{t}</Markdown>
          </motion.li>
        ))}
      </ul>
    </Card>
  );
}

/** Accuracy over the lesson's checks (the warm-up is a guess, so it doesn't count). */
function lessonAccuracy(lesson: Lesson, answers: Record<string, AnswerResult>): number | null {
  const scored = lesson.checks.filter((c) => c.afterSection >= 0 && answers[c.id]).map((c) => answers[c.id]!);
  if (scored.length === 0) return null;
  const points = scored.reduce((sum, a) => sum + (a.verdict === "CORRECT" ? 1 : a.verdict === "PARTIAL" ? 0.5 : 0), 0);
  return points / scored.length;
}

function formatDuration(seconds: number): string {
  const m = Math.floor(seconds / 60);
  const s = seconds % 60;
  return m > 0 ? `${m}:${String(s).padStart(2, "0")}` : `${s}s`;
}

function LessonComplete({
  lesson,
  answers,
  seconds,
  xpEarned,
  nextTopicTitle,
  primary,
  backTo,
}: {
  lesson: Lesson;
  answers: Record<string, AnswerResult>;
  seconds: number;
  xpEarned: number | null;
  nextTopicTitle?: string;
  primary: ReactNode;
  backTo: string;
}) {
  const accuracy = lessonAccuracy(lesson, answers);
  return (
    <motion.div className="ls-done" initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: "spring", stiffness: 300, damping: 24 }}>
      <Dia mood="cheer" size={150} />
      <div className="stack-sm">
        <h1>Lesson complete!</h1>
        <p className="text-2">You explained, practised and checked this idea. That's exactly how knowledge sticks.</p>
      </div>
      <div className="result-stats">
        <ResultStat tone="amber" label="XP earned" emoji="high-voltage" value={xpEarned != null ? `+${xpEarned}` : "+"} />
        <ResultStat tone="green" label="Accuracy" emoji="bullseye" value={accuracy != null ? `${Math.round(accuracy * 100)}%` : "–"} />
        <ResultStat tone="blue" label="Time" emoji="alarm-clock" value={formatDuration(seconds)} />
      </div>
      {lesson.keyTakeaways.length > 0 && (
        <div style={{ width: "100%" }}>
          <Takeaways items={lesson.keyTakeaways} />
        </div>
      )}
      <div className="stack-sm" style={{ width: "100%", alignItems: "center" }}>
        {primary}
        {nextTopicTitle && <span className="small muted">Up next: {nextTopicTitle}</span>}
        <ButtonLink to={backTo} variant="ghost">
          Back to path
        </ButtonLink>
      </div>
    </motion.div>
  );
}
