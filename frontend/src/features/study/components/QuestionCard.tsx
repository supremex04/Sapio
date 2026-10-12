import { Check, RotateCcw, X } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useLayoutEffect, useRef, useState, type ReactNode } from "react";
import { errorMessage } from "../../../shared/api/errors";
import { Dia, type DiaMood } from "../../../shared/illustrations/Dia";
import { Emoji } from "../../../shared/illustrations/Emoji";
import { formatRelative } from "../../../shared/lib/format";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { cx } from "../../../shared/ui/cx";
import { FeedbackSheet, type SheetTone } from "../../../shared/ui/FeedbackSheet";
import { Markdown } from "../../../shared/ui/Markdown";
import { CONFIDENCE_OPTIONS, isConfidentMiss, optionLetter, verdictCopy } from "../feedback";
import type { AnswerInput, AnswerResult, Confidence, QuestionType, Verdict } from "../types";
import "./question.css";

export interface QuestionLike {
  type: QuestionType;
  prompt: string;
  options: string[];
}

interface Props {
  question: QuestionLike;
  onSubmit: (input: AnswerInput) => Promise<AnswerResult>;
  initialResult?: AnswerResult | null;
  allowRetry?: boolean;
  context?: "warmup" | "normal";
  eyebrow?: ReactNode;
  hint?: ReactNode;
  onAnswered?: (result: AnswerResult) => void;
  /**
   * "sheet" (default): the active question; Check/feedback live in the sticky bottom FeedbackSheet.
   * "inline": a past question shown in a scroll-back (feedback rendered compactly in place).
   */
  presentation?: "sheet" | "inline";
  /** Shown as the sheet's primary button after answering (e.g. go to the next step). */
  onContinue?: () => void;
  continueLabel?: string;
}

const SHEET_TONE: Record<Verdict, SheetTone> = { CORRECT: "correct", PARTIAL: "partial", INCORRECT: "incorrect" };
const CONTINUE_VARIANT = { CORRECT: "success", PARTIAL: "accent", INCORRECT: "danger" } as const;

function moodFor(result: AnswerResult, context: "warmup" | "normal"): DiaMood {
  if (result.verdict === "CORRECT") return context === "warmup" ? "happy" : "cheer";
  if (isConfidentMiss(result)) return "thinking";
  return "encourage";
}

/**
 * One question: answer → (optional) confidence → Check → immediate elaborative feedback.
 * Shared by lesson checks, tests and reviews so the interaction is always the same.
 * Keyboard: 1–9 pick an option, Enter checks / continues (Ctrl+Enter inside the text box).
 */
export function QuestionCard({
  question,
  onSubmit,
  initialResult = null,
  allowRetry = false,
  context = "normal",
  eyebrow,
  hint,
  onAnswered,
  presentation = "sheet",
  onContinue,
  continueLabel = "Continue",
}: Props) {
  const [selected, setSelected] = useState<number | null>(initialResult?.selectedOptionIndex ?? null);
  const [text, setText] = useState(initialResult?.answerText ?? "");
  const [confidence, setConfidence] = useState<Confidence | null>(initialResult?.confidence ?? null);
  const [result, setResult] = useState<AnswerResult | null>(initialResult);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isShort = question.type === "SHORT_ANSWER";
  const canSubmit = !result && !submitting && (isShort ? text.trim().length > 0 : selected !== null);
  const sheet = presentation === "sheet";

  async function submit() {
    if (!canSubmit) return;
    setSubmitting(true);
    setError(null);
    try {
      const input: AnswerInput = isShort ? { text: text.trim() } : { optionIndex: selected ?? 0 };
      if (confidence) input.confidence = confidence;
      const r = await onSubmit(input);
      setResult(r);
      onAnswered?.(r);
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setSubmitting(false);
    }
  }

  function retry() {
    setResult(null);
    setSelected(null);
    setText("");
    setConfidence(null);
  }

  // Keyboard shortcuts for the active question.
  const latest = useRef({ submit, onContinue, result, canSubmit, isShort, optionCount: question.options.length });
  latest.current = { submit, onContinue, result, canSubmit, isShort, optionCount: question.options.length };
  useEffect(() => {
    if (!sheet) return;
    function onKey(e: KeyboardEvent) {
      const s = latest.current;
      const inText = e.target instanceof HTMLTextAreaElement || e.target instanceof HTMLInputElement;
      if (e.key === "Enter" && (!inText || e.metaKey || e.ctrlKey)) {
        if (s.result && s.onContinue) {
          e.preventDefault();
          s.onContinue();
        } else if (s.canSubmit) {
          e.preventDefault();
          void s.submit();
        }
        return;
      }
      if (!inText && !s.result && !s.isShort && /^[1-9]$/.test(e.key)) {
        const i = Number(e.key) - 1;
        if (i < s.optionCount) setSelected(i);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, [sheet]);

  return (
    <div className={cx("question", sheet && "question-active")}>
      {eyebrow && <p className="question-eyebrow">{eyebrow}</p>}
      <Markdown className="question-prompt">{question.prompt}</Markdown>
      {hint && !result && <p className="question-hint">{hint}</p>}

      {isShort ? (
        <textarea
          className="textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Explain it in your own words…"
          disabled={!!result || submitting}
          aria-label="Your answer"
          maxLength={4000}
        />
      ) : (
        <div className="options" role="radiogroup" aria-label="Answer options">
          {question.options.map((option, i) => {
            const isSelected = selected === i;
            const isCorrect = !!result && result.correctOptionIndex === i;
            const isWrongPick = !!result && isSelected && !isCorrect;
            return (
              <motion.button
                key={i}
                type="button"
                role="radio"
                aria-checked={isSelected}
                disabled={!!result || submitting}
                className={cx("option", !result && isSelected && "is-selected", isCorrect && "is-correct", isWrongPick && "is-incorrect", result && !isCorrect && !isWrongPick && "is-muted")}
                onClick={() => setSelected(i)}
                animate={isWrongPick ? { x: [0, -6, 6, -4, 4, 0] } : isCorrect ? { scale: [1, 1.03, 1] } : {}}
                transition={{ duration: 0.4 }}
              >
                <span className="option-key" aria-hidden>
                  {isCorrect ? <Check size={18} strokeWidth={3.5} /> : isWrongPick ? <X size={18} strokeWidth={3.5} /> : optionLetter(i)}
                </span>
                <span className="option-text">
                  <Markdown>{option}</Markdown>
                </span>
              </motion.button>
            );
          })}
        </div>
      )}

      {!result && (
        <div className="confidence" role="radiogroup" aria-label="How sure are you?">
          <span className="confidence-label">How sure are you?</span>
          {CONFIDENCE_OPTIONS.map((c) => (
            <button
              key={c.value}
              type="button"
              role="radio"
              aria-checked={confidence === c.value}
              className={cx("chip", "confidence-option")}
              aria-pressed={confidence === c.value}
              onClick={() => setConfidence(confidence === c.value ? null : c.value)}
            >
              <Emoji name={c.emoji} size={20} />
              {c.label}
            </button>
          ))}
        </div>
      )}

      {error && <Alert>{error}</Alert>}

      {sheet ? (
        <SheetSlot>
          {result ? (
            <FeedbackSheet
              tone={SHEET_TONE[result.verdict]}
              mood={moodFor(result, context)}
              title={verdictCopy(result.verdict, context).title}
              aside={
                <div className="row">
                  {result.nextReviewAt && <span className="sheet-note">Back for recall {formatRelative(result.nextReviewAt)}</span>}
                  {allowRetry && result.verdict !== "CORRECT" && (
                    <Button size="sm" variant="ghost" icon={<RotateCcw size={14} />} onClick={retry}>
                      Try again
                    </Button>
                  )}
                </div>
              }
              actions={
                onContinue && (
                  <Button variant={CONTINUE_VARIANT[result.verdict]} size="lg" onClick={onContinue} autoFocus>
                    {continueLabel}
                  </Button>
                )
              }
            >
              <FeedbackBody result={result} />
            </FeedbackSheet>
          ) : (
            <FeedbackSheet
              tone="neutral"
              aside={isShort && <span className="sheet-note">Ctrl + Enter to submit</span>}
              actions={
                <Button variant="primary" size="lg" onClick={submit} disabled={!canSubmit} loading={submitting}>
                  {isShort ? (submitting ? "Reading…" : "Submit") : "Check"}
                </Button>
              }
            />
          )}
        </SheetSlot>
      ) : result ? (
        <div className={cx("feedback-inline", `feedback-inline-${SHEET_TONE[result.verdict]}`)}>
          <div className="row">
            <Dia mood={moodFor(result, context)} size={36} still />
            <strong>{verdictCopy(result.verdict, context).title}</strong>
          </div>
          <FeedbackBody result={result} />
        </div>
      ) : null}
    </div>
  );
}

function FeedbackBody({ result }: { result: AnswerResult }) {
  const showFeedback = result.feedback && result.feedback.trim() !== result.explanation.trim();
  return (
    <div className="feedback-body">
      {isConfidentMiss(result) && <p className="feedback-hypercorrect">Great catch: confident mistakes are the ones you'll remember best once corrected.</p>}
      {showFeedback && <Markdown className="feedback-text">{result.feedback}</Markdown>}
      {result.referenceAnswer && (
        <div className="feedback-reference">
          <p className="eyebrow">A strong answer</p>
          <Markdown>{result.referenceAnswer}</Markdown>
        </div>
      )}
      {result.explanation && result.explanation !== result.referenceAnswer && <Markdown className="feedback-why">{result.explanation}</Markdown>}
    </div>
  );
}

/** Reserves space at the end of the page equal to the fixed sheet's height, so nothing hides under it. */
function SheetSlot({ children }: { children: ReactNode }) {
  const ref = useRef<HTMLDivElement>(null);
  const [height, setHeight] = useState(0);
  useLayoutEffect(() => {
    const sheetEl = ref.current?.querySelector(".sheet");
    if (!sheetEl) return;
    const observer = new ResizeObserver(() => setHeight(sheetEl.getBoundingClientRect().height));
    observer.observe(sheetEl);
    return () => observer.disconnect();
  });
  return (
    <div ref={ref}>
      <div style={{ height }} aria-hidden />
      {children}
    </div>
  );
}
