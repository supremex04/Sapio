import { CheckCircle2, CircleDashed, Lightbulb, RotateCcw, Send, Sparkles } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useState, type ReactNode } from "react";
import { errorMessage } from "../../../shared/api/errors";
import { formatRelative } from "../../../shared/lib/format";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { cx } from "../../../shared/ui/cx";
import { Markdown } from "../../../shared/ui/Markdown";
import { CONFIDENCE_OPTIONS, isConfidentMiss, optionLetter, verdictCopy } from "../feedback";
import type { AnswerInput, AnswerResult, Confidence, QuestionType } from "../types";

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
}

/**
 * One question: answer → (optional) confidence → submit → immediate elaborative feedback.
 * Shared by lesson checks, assessments and reviews so the interaction is always the same.
 */
export function QuestionCard({ question, onSubmit, initialResult = null, allowRetry = false, context = "normal", eyebrow, hint, onAnswered }: Props) {
  const [selected, setSelected] = useState<number | null>(initialResult?.selectedOptionIndex ?? null);
  const [text, setText] = useState(initialResult?.answerText ?? "");
  const [confidence, setConfidence] = useState<Confidence | null>(initialResult?.confidence ?? null);
  const [result, setResult] = useState<AnswerResult | null>(initialResult);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  const isShort = question.type === "SHORT_ANSWER";
  const canSubmit = !result && !submitting && (isShort ? text.trim().length > 0 : selected !== null);

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

  return (
    <div className="question">
      {eyebrow && <p className="question-eyebrow">{eyebrow}</p>}
      <Markdown className="question-prompt">{question.prompt}</Markdown>
      {hint && !result && <p className="question-hint">{hint}</p>}

      {isShort ? (
        <textarea
          className="textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Explain in your own words…"
          disabled={!!result || submitting}
          aria-label="Your answer"
          maxLength={4000}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) void submit();
          }}
        />
      ) : (
        <div className="options" role="radiogroup" aria-label="Answer options">
          {question.options.map((option, i) => {
            const isSelected = selected === i;
            const isCorrect = result?.correctOptionIndex === i;
            const isWrongPick = !!result && isSelected && !isCorrect;
            return (
              <motion.button
                key={i}
                type="button"
                role="radio"
                aria-checked={isSelected}
                disabled={!!result || submitting}
                className={cx("option", isSelected && "selected", result && isCorrect && "correct", isWrongPick && "wrong")}
                onClick={() => setSelected(i)}
                animate={isWrongPick ? { x: [0, -5, 5, -3, 3, 0] } : isCorrect && result ? { scale: [1, 1.02, 1] } : {}}
                transition={{ duration: 0.4 }}
              >
                <span className="option-letter" aria-hidden>
                  {result && isCorrect ? <CheckCircle2 size={16} /> : optionLetter(i)}
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
        <div className="question-footer">
          <div className="confidence" role="radiogroup" aria-label="How sure are you?">
            <span className="xs muted">How sure are you?</span>
            {CONFIDENCE_OPTIONS.map((c) => (
              <button key={c.value} type="button" role="radio" aria-checked={confidence === c.value} className={cx("confidence-option", confidence === c.value && "selected")} onClick={() => setConfidence(confidence === c.value ? null : c.value)}>
                {c.label}
              </button>
            ))}
          </div>
          <Button variant="primary" onClick={submit} disabled={!canSubmit} loading={submitting} icon={<Send size={16} />}>
            {isShort ? (submitting ? "Reading your answer…" : "Submit") : "Check"}
          </Button>
        </div>
      )}

      {error && <Alert>{error}</Alert>}

      <AnimatePresence>{result && <FeedbackPanel result={result} context={context} onRetry={allowRetry && result.verdict !== "CORRECT" ? retry : undefined} />}</AnimatePresence>
    </div>
  );
}

function FeedbackPanel({ result, context, onRetry }: { result: AnswerResult; context: "warmup" | "normal"; onRetry?: () => void }) {
  const { title, tone } = verdictCopy(result.verdict, context);
  const Icon = tone === "success" ? CheckCircle2 : CircleDashed;
  const showFeedback = result.feedback && result.feedback.trim() !== result.explanation.trim();
  return (
    <motion.div className={cx("feedback", `feedback-${tone}`)} initial={{ opacity: 0, y: 8 }} animate={{ opacity: 1, y: 0 }} transition={{ type: "spring", stiffness: 380, damping: 30 }} role="status">
      <div className="feedback-head">
        <motion.span className="feedback-icon" initial={{ scale: 0.5, rotate: -20 }} animate={{ scale: 1, rotate: 0 }} transition={{ type: "spring", stiffness: 500, damping: 18 }}>
          <Icon size={20} />
        </motion.span>
        <strong>{title}</strong>
        {result.verdict !== "CORRECT" && result.score > 0 && <span className="xs muted">{Math.round(result.score * 100)}%</span>}
      </div>
      {isConfidentMiss(result) && (
        <p className="feedback-hypercorrect">
          <Sparkles size={14} aria-hidden /> Great catch: confident mistakes are the ones you'll remember best once corrected.
        </p>
      )}
      {showFeedback && <Markdown className="feedback-text">{result.feedback}</Markdown>}
      {result.referenceAnswer && (
        <div className="feedback-reference">
          <p className="xs eyebrow">A strong answer</p>
          <Markdown>{result.referenceAnswer}</Markdown>
        </div>
      )}
      {result.explanation && (
        <div className="feedback-why">
          <Lightbulb size={16} aria-hidden />
          <Markdown>{result.explanation}</Markdown>
        </div>
      )}
      <div className="row-between">
        {result.nextReviewAt ? <span className="xs muted">You'll see this again {formatRelative(result.nextReviewAt)}.</span> : <span />}
        {onRetry && (
          <Button size="sm" variant="ghost" icon={<RotateCcw size={14} />} onClick={onRetry}>
            Try again
          </Button>
        )}
      </div>
    </motion.div>
  );
}
