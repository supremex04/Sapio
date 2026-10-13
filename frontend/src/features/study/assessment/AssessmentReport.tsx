import { Check, ChevronDown, X } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useState, type CSSProperties } from "react";
import { Sage, type SageMood } from "../../../shared/illustrations/Sage";
import { Emoji } from "../../../shared/illustrations/Emoji";
import type { IconName } from "../../../shared/illustrations/Emoji";
import { formatPercent } from "../../../shared/lib/format";
import { Badge, type Tone } from "../../../shared/ui/Badge";
import { ButtonLink } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { cx } from "../../../shared/ui/cx";
import { FocusHeader } from "../../../shared/ui/FocusHeader";
import { Markdown } from "../../../shared/ui/Markdown";
import { ProgressBar, ProgressRing } from "../../../shared/ui/Progress";
import { Segmented } from "../../../shared/ui/Segmented";
import { FeedbackBody } from "../components/QuestionCard";
import { ResultStat } from "../components/ResultStat";
import "../components/question.css";
import { optionLetter } from "../feedback";
import type { Assessment, AssessmentItem, Calibration, TopicLevel } from "../types";
import "./assessment.css";

const LEVEL_META: Record<TopicLevel, { label: string; tone: Tone; color: string }> = {
  STRONG: { label: "Strong", tone: "success", color: "var(--green-main)" },
  DEVELOPING: { label: "Developing", tone: "primary", color: "var(--blue-main)" },
  WEAK: { label: "Focus here", tone: "warning", color: "var(--amber-main)" },
};

/** Growth-mindset headline + Sapi's mood for a score. */
export function reportHeadline(score: number): { title: string; sub: string; mood: SageMood } {
  if (score >= 0.8) return { title: "Excellent work!", sub: "You clearly know this material. Spaced recall will keep it fresh.", mood: "cheer" };
  if (score >= 0.5) return { title: "Solid progress", sub: "You've got the foundations. A little focused practice will take you further.", mood: "happy" };
  return { title: "A great starting point", sub: "Every question you missed is now a recall card. This is exactly how learning works.", mood: "encourage" };
}

function scoreColor(score: number): string {
  return score >= 0.8 ? "var(--green-main)" : score >= 0.5 ? "var(--amber-main)" : "var(--blue-main)";
}

/** Report order follows the peak-end rule: celebrate, strengths, then what to work on. */
export function AssessmentReportView({ assessment, fresh }: { assessment: Assessment; fresh: boolean }) {
  const report = assessment.report!;
  const { title, sub, mood } = reportHeadline(report.score);
  const docId = assessment.documentId;
  const toReview = report.incorrectCount + report.skippedCount;

  return (
    <>
      <FocusHeader exitTo={`/documents/${docId}`} exitLabel="Back to course" progress={1} progressLabel="Test complete" />
      <div className="rp">
        <motion.section className="rp-hero" initial={{ opacity: 0, scale: 0.96 }} animate={{ opacity: 1, scale: 1 }} transition={{ type: "spring", stiffness: 300, damping: 24 }}>
          <Sage mood={mood} size={128} />
          <div className="rp-hero-text">
            {fresh && <p className="eyebrow">Test complete · {assessment.documentTitle}</p>}
            <h1>{title}</h1>
            <p className="text-2">{sub}</p>
          </div>
          <ProgressRing value={report.score} size={136} stroke={14} label="Score" color={scoreColor(report.score)}>
            <span className="rp-score">{formatPercent(report.score)}</span>
          </ProgressRing>
        </motion.section>

        <div className="result-stats">
          <ResultStat tone="green" label="Correct" emoji="check-mark" value={report.correctCount} />
          <ResultStat tone="amber" label="Partly" emoji="thinking-face" value={report.partialCount} />
          <ResultStat tone="teal" label="To recall" emoji="recall" value={toReview} />
        </div>

        <div className="rp-actions">
          {toReview + report.partialCount > 0 && (
            <ButtonLink to={`/recall?documentId=${docId}`} variant="review" size="lg">
              Recall mistakes
            </ButtonLink>
          )}
          <ButtonLink to={`/documents/${docId}`} size="lg">
            Back to course
          </ButtonLink>
        </div>

        {report.summary && (
          <Card>
            <Markdown>{report.summary}</Markdown>
          </Card>
        )}

        {report.strengths.length > 0 && (
          <Card tone="green">
            <SectionTitle emoji="flexed-biceps" title="What you did well" />
            <ul className="rp-list">
              {report.strengths.map((s, i) => (
                <li key={i}>
                  <Markdown>{s}</Markdown>
                </li>
              ))}
            </ul>
          </Card>
        )}

        <Card>
          <SectionTitle emoji="card-index-dividers" title="By topic" />
          <ul className="rp-topics">
            {report.topics.map((t) => {
              const meta = LEVEL_META[t.level];
              return (
                <li key={t.topicId}>
                  <div className="row-between">
                    <span>
                      <strong>{t.title}</strong> <span className="small muted">· {t.questionCount} q</span>
                    </span>
                    <Badge tone={meta.tone}>
                      {meta.label} · {formatPercent(t.score)}
                    </Badge>
                  </div>
                  <ProgressBar value={t.score} color={meta.color} size="sm" label={`${t.title} score`} />
                </li>
              );
            })}
          </ul>
        </Card>

        {report.weakAreas.length > 0 && (
          <section className="stack-sm">
            <SectionTitle emoji="bullseye" title="Where to grow next" />
            {report.weakAreas.map((w, i) => (
              <Card key={i} tone="amber" tight className="rp-weak">
                <div className="rp-weak-text">
                  <strong>{w.title}</strong>
                  <Markdown className="small">{w.advice}</Markdown>
                </div>
                {w.topicId && (
                  <ButtonLink to={`/documents/${docId}/learn/${w.topicId}`} size="sm" variant="accent">
                    Review lesson
                  </ButtonLink>
                )}
              </Card>
            ))}
          </section>
        )}

        {report.nextSteps.length > 0 && (
          <Card>
            <SectionTitle emoji="rocket" title="Next steps" />
            <ol className="rp-list rp-list-numbered">
              {report.nextSteps.map((s, i) => (
                <li key={i}>
                  <Markdown>{s}</Markdown>
                </li>
              ))}
            </ol>
            <div className="row" style={{ marginTop: "var(--space-4)" }}>
              <ButtonLink to={`/documents/${docId}/test`} variant="mastery">
                New test
              </ButtonLink>
              <ButtonLink to={`/documents/${docId}`} variant="ghost">
                Learning path
              </ButtonLink>
            </div>
          </Card>
        )}

        {report.calibration && <CalibrationCard calibration={report.calibration} />}

        <QuestionReview items={assessment.items} />
      </div>
    </>
  );
}

function SectionTitle({ emoji, title }: { emoji: IconName; title: string }) {
  return (
    <h2 className="rp-section-title">
      <Emoji name={emoji} size={28} /> {title}
    </h2>
  );
}

/** "How well did you know what you knew?" Metacognition, framed positively. */
function CalibrationCard({ calibration }: { calibration: Calibration }) {
  const total = calibration.confidentCorrect + calibration.confidentIncorrect + calibration.unsureCorrect + calibration.unsureIncorrect;
  return (
    <Card>
      <SectionTitle emoji="brain" title="How well did you know what you knew?" />
      {total === 0 ? (
        <p className="small muted">Rate your confidence before answering next time to see this.</p>
      ) : (
        <>
          <div className="cal" role="table" aria-label="Confidence versus correctness">
            <div role="row" className="cal-row">
              <span role="columnheader" />
              <span role="columnheader" className="cal-axis">
                Correct
              </span>
              <span role="columnheader" className="cal-axis">
                Missed
              </span>
            </div>
            <div role="row" className="cal-row">
              <span role="rowheader" className="cal-axis">
                Sure
              </span>
              <CalCell n={calibration.confidentCorrect} title="Knew it, and knew you knew" tone="green" total={total} />
              <CalCell n={calibration.confidentIncorrect} title="Confident misses: your best learning moments" tone="amber" total={total} />
            </div>
            <div role="row" className="cal-row">
              <span role="rowheader" className="cal-axis">
                Unsure
              </span>
              <CalCell n={calibration.unsureCorrect} title="Knew more than you thought" tone="teal" total={total} />
              <CalCell n={calibration.unsureIncorrect} title="Honest gaps, now in your recall deck" tone="red" total={total} />
            </div>
          </div>
          <p className="xs muted" style={{ marginTop: "var(--space-3)" }}>
            Noticing what you're unsure about is a skill, and it gets sharper every time you rate yourself.
          </p>
        </>
      )}
    </Card>
  );
}

function CalCell({ n, title, tone, total }: { n: number; title: string; tone: "green" | "amber" | "teal" | "red"; total: number }) {
  return (
    <motion.div className={`cal-cell cal-${tone}`} role="cell" initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} style={{ "--cal-share": total ? n / total : 0 } as CSSProperties}>
      <strong className="tabular">{n}</strong>
      <span className="xs">{title}</span>
    </motion.div>
  );
}

const VERDICT_EMOJI: Record<string, IconName> = {
  CORRECT: "check-mark",
  PARTIAL: "thinking-face",
  INCORRECT: "cross-mark",
  SKIPPED: "hourglass-not-done",
};

type ReviewFilter = "mistakes" | "all";

/**
 * Every question with its full check: the options marked right / wrong (or the written answer), the grader's
 * feedback, a strong answer and the explanation. Tests reveal nothing while running, so this is where
 * the learning from the test happens; mistakes come first and open.
 */
function QuestionReview({ items }: { items: AssessmentItem[] }) {
  const isMistake = (it: AssessmentItem) => it.answer?.verdict !== "CORRECT";
  const mistakes = items.filter(isMistake);
  const [filter, setFilter] = useState<ReviewFilter>(mistakes.length > 0 ? "mistakes" : "all");
  const [open, setOpen] = useState<Set<number>>(() => new Set(mistakes.map((it) => it.position)));
  const shown = filter === "mistakes" ? mistakes : items;
  const toggle = (position: number) =>
    setOpen((s) => {
      const next = new Set(s);
      if (!next.delete(position)) next.add(position);
      return next;
    });

  return (
    <Card>
      <div className="row-between qr-title">
        <SectionTitle emoji="magnifying-glass-tilted-left" title="Go through your answers" />
        <Segmented<ReviewFilter>
          label="Show"
          value={filter}
          onChange={setFilter}
          options={[
            { value: "mistakes", label: `To strengthen (${mistakes.length})` },
            { value: "all", label: `All (${items.length})` },
          ]}
        />
      </div>
      {shown.length === 0 ? (
        <p className="small muted">Nothing to strengthen: every answer was right.</p>
      ) : (
        <ul className="qr">
          {shown.map((it) => {
            const a = it.answer;
            const isOpen = open.has(it.position);
            const verdict = a?.verdict ?? "SKIPPED";
            return (
              <li key={it.position} className="qr-item">
                <button type="button" className="qr-head" aria-expanded={isOpen} onClick={() => toggle(it.position)}>
                  <Emoji name={VERDICT_EMOJI[verdict] ?? "cross-mark"} size={22} label={verdict.toLowerCase()} />
                  <span className="qr-prompt">
                    {it.position}. <Markdown className="qr-md">{it.question.prompt}</Markdown>
                  </span>
                  <ChevronDown size={18} strokeWidth={2.5} className={cx("qr-chev", isOpen && "is-open")} aria-hidden />
                </button>
                <AnimatePresence initial={false}>
                  {isOpen && (
                    <motion.div className="qr-body" initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }}>
                      <div className="stack-sm qr-body-inner">
                        {!a ? (
                          <p className="small muted">Skipped. It's in your recall deck, so it will come back soon.</p>
                        ) : (
                          <>
                            {it.question.options.length > 0 ? (
                              <div className="options qr-options" aria-label="Options">
                                {it.question.options.map((option, i) => {
                                  const correct = a.correctOptionIndex === i;
                                  const wrongPick = a.selectedOptionIndex === i && !correct;
                                  return (
                                    <div key={i} className={cx("option", correct && "is-correct", wrongPick && "is-incorrect", !correct && !wrongPick && "is-muted")}>
                                      <span className="option-key" aria-hidden>
                                        {correct ? <Check size={18} strokeWidth={3.5} /> : wrongPick ? <X size={18} strokeWidth={3.5} /> : optionLetter(i)}
                                      </span>
                                      <span className="option-text">
                                        <Markdown>{option}</Markdown>
                                        {a.selectedOptionIndex === i && <span className="xs qr-yours">Your answer</span>}
                                      </span>
                                    </div>
                                  );
                                })}
                              </div>
                            ) : (
                              a.answerText && (
                                <div className="qr-written">
                                  <p className="eyebrow">Your answer</p>
                                  <p className="small">{a.answerText}</p>
                                </div>
                              )
                            )}
                            <FeedbackBody result={a} />
                          </>
                        )}
                      </div>
                    </motion.div>
                  )}
                </AnimatePresence>
              </li>
            );
          })}
        </ul>
      )}
    </Card>
  );
}
