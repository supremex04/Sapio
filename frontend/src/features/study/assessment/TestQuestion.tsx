import { useEffect, useRef, useState } from "react";
import { Emoji } from "../../../shared/illustrations/Emoji";
import { Button } from "../../../shared/ui/Button";
import { cx } from "../../../shared/ui/cx";
import { FeedbackSheet } from "../../../shared/ui/FeedbackSheet";
import { Markdown } from "../../../shared/ui/Markdown";
import { CONFIDENCE_OPTIONS, optionLetter } from "../feedback";
import type { AnswerInput, Confidence, QuestionView, SubmittedAnswer } from "../types";
import { SheetSlot } from "../components/QuestionCard";
import "../components/question.css";

/**
 * One test question. Collects the answer (and how sure the learner is) and moves on: tests show no results
 * until the report, so there is no feedback here. A saved answer is shown read-only when revisited.
 * Keyboard: 1–9 pick an option, Enter saves and continues (Ctrl+Enter in the text box).
 */
export function TestQuestion({ question, submitted, onSave, nextLabel }: {
  question: QuestionView;
  submitted: SubmittedAnswer | null;
  /** Saves in the background; the runner moves on at once. */
  onSave: (input: AnswerInput) => void;
  nextLabel: string;
}) {
  const [selected, setSelected] = useState<number | null>(submitted?.selectedOptionIndex ?? null);
  const [text, setText] = useState(submitted?.answerText ?? "");
  const [confidence, setConfidence] = useState<Confidence | null>(submitted?.confidence ?? null);
  const isShort = question.type === "SHORT_ANSWER";
  const locked = submitted !== null;
  const canSave = !locked && (isShort ? text.trim().length > 0 : selected !== null);

  function save() {
    if (!canSave) return;
    const input: AnswerInput = isShort ? { text: text.trim() } : { optionIndex: selected ?? 0 };
    if (confidence) input.confidence = confidence;
    onSave(input);
  }

  const latest = useRef({ save, canSave, locked, isShort, optionCount: question.options.length });
  latest.current = { save, canSave, locked, isShort, optionCount: question.options.length };
  useEffect(() => {
    function onKey(e: KeyboardEvent) {
      const s = latest.current;
      const inText = e.target instanceof HTMLTextAreaElement || e.target instanceof HTMLInputElement;
      if (e.key === "Enter" && (!inText || e.metaKey || e.ctrlKey) && s.canSave) {
        e.preventDefault();
        s.save();
      } else if (!inText && !s.locked && !s.isShort && /^[1-9]$/.test(e.key)) {
        const i = Number(e.key) - 1;
        if (i < s.optionCount) setSelected(i);
      }
    }
    window.addEventListener("keydown", onKey);
    return () => window.removeEventListener("keydown", onKey);
  }, []);

  return (
    <div className="question question-active">
      <Markdown className="question-prompt">{question.prompt}</Markdown>

      {isShort ? (
        <textarea
          className="textarea"
          value={text}
          onChange={(e) => setText(e.target.value)}
          placeholder="Explain it in your own words…"
          disabled={locked}
          aria-label="Your answer"
          maxLength={4000}
        />
      ) : (
        <div className="options" role="radiogroup" aria-label="Answer options">
          {question.options.map((option, i) => (
            <button
              key={i}
              type="button"
              role="radio"
              aria-checked={selected === i}
              disabled={locked}
              className={cx("option", selected === i && "is-selected", locked && selected !== i && "is-muted")}
              onClick={() => setSelected(i)}
            >
              <span className="option-key" aria-hidden>
                {optionLetter(i)}
              </span>
              <span className="option-text">
                <Markdown>{option}</Markdown>
              </span>
            </button>
          ))}
        </div>
      )}

      {!locked && (
        <div className="confidence" role="radiogroup" aria-label="How sure are you?">
          <span className="confidence-label">How sure are you?</span>
          {CONFIDENCE_OPTIONS.map((c) => (
            <button
              key={c.value}
              type="button"
              role="radio"
              aria-checked={confidence === c.value}
              aria-pressed={confidence === c.value}
              className={cx("chip", "confidence-option")}
              onClick={() => setConfidence(confidence === c.value ? null : c.value)}
            >
              <Emoji name={c.emoji} size={20} />
              {c.label}
            </button>
          ))}
        </div>
      )}

      <SheetSlot>
        <FeedbackSheet
          tone="neutral"
          aside={<span className="sheet-note">{locked ? "Answer saved. Results come at the end." : "You'll see how you did at the end."}</span>}
          actions={
            !locked && (
              <Button variant="primary" size="lg" onClick={save} disabled={!canSave}>
                {nextLabel}
              </Button>
            )
          }
        />
      </SheetSlot>
    </div>
  );
}
