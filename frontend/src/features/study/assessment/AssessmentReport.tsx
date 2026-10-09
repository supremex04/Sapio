import { ArrowRight, BookOpen, CheckCircle2, ChevronDown, CircleDashed, Layers, MinusCircle, RotateCcw, Sparkles, Star, Target, TrendingUp } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useState } from "react";
import { Link } from "react-router";
import { formatPercent } from "../../../shared/lib/format";
import { Badge } from "../../../shared/ui/Badge";
import { ButtonLink } from "../../../shared/ui/Button";
import { Card, CardHeader } from "../../../shared/ui/Card";
import { cx } from "../../../shared/ui/cx";
import { Markdown } from "../../../shared/ui/Markdown";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { ProgressBar, ProgressRing } from "../../../shared/ui/Progress";
import { optionLetter } from "../feedback";
import type { Assessment, AssessmentItem, Calibration, TopicLevel } from "../types";

const LEVEL_META: Record<TopicLevel, { label: string; tone: "success" | "primary" | "warning"; color: string; icon: typeof Star }> = {
  STRONG: { label: "Strong", tone: "success", color: "var(--color-success)", icon: Star },
  DEVELOPING: { label: "Developing", tone: "primary", color: "var(--color-primary)", icon: TrendingUp },
  WEAK: { label: "Focus here", tone: "warning", color: "var(--color-warning)", icon: Target },
};

function headline(score: number): { title: string; sub: string } {
  if (score >= 0.8) return { title: "Excellent work!", sub: "You clearly know this material. Spaced reviews will keep it fresh." };
  if (score >= 0.5) return { title: "Solid progress", sub: "You've got the foundations. A little focused practice will take you further." };
  return { title: "A great starting point", sub: "Every question you missed is now a review card. This is exactly how learning works." };
}

/** Report order follows the peak-end rule: celebrate, strengths, then what to work on. */
export function AssessmentReportView({ assessment, fresh }: { assessment: Assessment; fresh: boolean }) {
  const report = assessment.report!;
  const { title, sub } = headline(report.score);
  const docId = assessment.documentId;

  return (
    <PageTransition>
      <div className="report stack-lg">
        <Link to={`/documents/${docId}`} className="back-link" style={{ marginBottom: 0 }}>
          ← {assessment.documentTitle}
        </Link>

        <Card className="report-hero">
          <ProgressRing value={report.score} size={150} stroke={12} label="Score" color={report.score >= 0.8 ? "var(--color-success)" : undefined}>
            <span className="report-score">{formatPercent(report.score)}</span>
            <span className="xs muted">score</span>
          </ProgressRing>
          <div className="stack-sm" style={{ flex: 1, minWidth: 240 }}>
            {fresh && <p className="eyebrow">Test complete</p>}
            <h1>{title}</h1>
            <p className="text-2">{sub}</p>
            <div className="report-counts">
              <Count icon={<CheckCircle2 size={16} />} tone="success" n={report.correctCount} label="correct" />
              <Count icon={<CircleDashed size={16} />} tone="warning" n={report.partialCount} label="partly" />
              <Count icon={<RotateCcw size={16} />} tone="neutral" n={report.incorrectCount} label="to review" />
              {report.skippedCount > 0 && <Count icon={<MinusCircle size={16} />} tone="neutral" n={report.skippedCount} label="skipped" />}
            </div>
          </div>
        </Card>

        {report.strengths.length > 0 && (
          <Card>
            <CardHeader icon={<Sparkles size={18} />} title="What you did well" />
            <ul className="bullet-list">
              {report.strengths.map((s, i) => (
                <li key={i}>
                  <Markdown>{s}</Markdown>
                </li>
              ))}
            </ul>
          </Card>
        )}

        {report.summary && (
          <Card>
            <Markdown>{report.summary}</Markdown>
          </Card>
        )}

        <div className="report-grid">
          <Card>
            <CardHeader icon={<Layers size={18} />} title="By topic" />
            <ul className="topic-results">
              {report.topics.map((t) => {
                const meta = LEVEL_META[t.level];
                const Icon = meta.icon;
                return (
                  <li key={t.topicId}>
                    <div className="row-between">
                      <span className="small">
                        <strong>{t.title}</strong> <span className="muted">· {t.questionCount} q</span>
                      </span>
                      <Badge tone={meta.tone} icon={<Icon />}>
                        {meta.label} · {formatPercent(t.score)}
                      </Badge>
                    </div>
                    <ProgressBar value={t.score} color={meta.color} size="sm" label={`${t.title} score`} />
                  </li>
                );
              })}
            </ul>
          </Card>
          {report.calibration && <CalibrationCard calibration={report.calibration} />}
        </div>

        {report.weakAreas.length > 0 && (
          <Card>
            <CardHeader icon={<Target size={18} />} title="Where to grow next" />
            <div className="weak-list">
              {report.weakAreas.map((w, i) => (
                <div key={i} className="weak-item">
                  <div style={{ flex: 1 }}>
                    <strong>{w.title}</strong>
                    <Markdown className="small">{w.advice}</Markdown>
                  </div>
                  {w.topicId && (
                    <ButtonLink to={`/documents/${docId}/learn/${w.topicId}`} size="sm" variant="soft" icon={<BookOpen size={14} />}>
                      Review in Learn
                    </ButtonLink>
                  )}
                </div>
              ))}
            </div>
          </Card>
        )}

        {report.nextSteps.length > 0 && (
          <Card>
            <CardHeader icon={<ArrowRight size={18} />} title="Next steps" />
            <ol className="bullet-list numbered">
              {report.nextSteps.map((s, i) => (
                <li key={i}>
                  <Markdown>{s}</Markdown>
                </li>
              ))}
            </ol>
            <div className="row" style={{ marginTop: "var(--space-4)" }}>
              <ButtonLink to={`/review?documentId=${docId}`} variant="primary" icon={<Layers size={16} />}>
                Review my mistakes
              </ButtonLink>
              <ButtonLink to={`/documents/${docId}/test`} variant="secondary">
                New test
              </ButtonLink>
            </div>
          </Card>
        )}

        <QuestionReview items={assessment.items} />
      </div>
    </PageTransition>
  );
}

function Count({ icon, n, label, tone }: { icon: React.ReactNode; n: number; label: string; tone: "success" | "warning" | "neutral" }) {
  return (
    <span className={cx("count", `count-${tone}`)}>
      {icon}
      <strong className="tabular">{n}</strong> {label}
    </span>
  );
}

/** "How well did you know what you knew?" Metacognition, framed positively. */
function CalibrationCard({ calibration }: { calibration: Calibration }) {
  const total = calibration.confidentCorrect + calibration.confidentIncorrect + calibration.unsureCorrect + calibration.unsureIncorrect;
  const cells = [
    { id: "cc", n: calibration.confidentCorrect, title: "Knew it, and knew you knew", tone: "success" },
    { id: "uc", n: calibration.unsureCorrect, title: "Knew more than you thought", tone: "primary" },
    { id: "ci", n: calibration.confidentIncorrect, title: "Confident misses: your best learning moments", tone: "accent" },
    { id: "ui", n: calibration.unsureIncorrect, title: "Honest gaps, now on your review list", tone: "neutral" },
  ];
  return (
    <Card>
      <CardHeader icon={<Target size={18} />} title="How well did you know what you knew?" />
      {total === 0 ? (
        <p className="small muted">Rate your confidence before answering next time to see this.</p>
      ) : (
        <>
          <div className="calibration" role="table" aria-label="Confidence versus correctness">
            <span className="cal-axis" />
            <span className="cal-axis xs muted">Correct</span>
            <span className="cal-axis xs muted">Missed</span>
            <span className="cal-axis xs muted">Sure</span>
            {cells.slice(0, 1).concat(cells.slice(2, 3)).map((c) => (
              <CalCell key={c.id} n={c.n} title={c.title} tone={c.tone} total={total} />
            ))}
            <span className="cal-axis xs muted">Unsure</span>
            {cells.slice(1, 2).concat(cells.slice(3, 4)).map((c) => (
              <CalCell key={c.id} n={c.n} title={c.title} tone={c.tone} total={total} />
            ))}
          </div>
          <p className="xs muted" style={{ marginTop: "var(--space-3)" }}>
            Noticing what you're unsure about is a skill, and it gets sharper every time you rate yourself.
          </p>
        </>
      )}
    </Card>
  );
}

function CalCell({ n, title, tone, total }: { n: number; title: string; tone: string; total: number }) {
  return (
    <motion.div className={`cal-cell cal-${tone}`} initial={{ opacity: 0, scale: 0.9 }} animate={{ opacity: 1, scale: 1 }} role="cell" title={title} style={{ "--cal-strength": total ? 0.15 + (0.85 * n) / total : 0 } as React.CSSProperties}>
      <strong className="tabular">{n}</strong>
      <span className="xs">{title}</span>
    </motion.div>
  );
}

function QuestionReview({ items }: { items: AssessmentItem[] }) {
  const [open, setOpen] = useState<number | null>(null);
  return (
    <Card>
      <CardHeader icon={<Layers size={18} />} title="Review every question" />
      <ul className="qreview">
        {items.map((it) => {
          const a = it.answer;
          const isOpen = open === it.position;
          const verdict = a?.verdict ?? "SKIPPED";
          return (
            <li key={it.position} className={`qreview-item qv-${verdict.toLowerCase()}`}>
              <button className="qreview-head" aria-expanded={isOpen} onClick={() => setOpen(isOpen ? null : it.position)}>
                <span className="qreview-mark" aria-hidden>
                  {verdict === "CORRECT" ? <CheckCircle2 size={16} /> : verdict === "PARTIAL" ? <CircleDashed size={16} /> : verdict === "SKIPPED" ? <MinusCircle size={16} /> : <RotateCcw size={16} />}
                </span>
                <span className="qreview-prompt">
                  {it.position}. <Markdown className="inline-md">{it.question.prompt}</Markdown>
                </span>
                <ChevronDown size={16} className={cx("chev", isOpen && "open")} aria-hidden />
              </button>
              <AnimatePresence initial={false}>
                {isOpen && (
                  <motion.div className="qreview-body" initial={{ height: 0, opacity: 0 }} animate={{ height: "auto", opacity: 1 }} exit={{ height: 0, opacity: 0 }}>
                    <div className="stack-sm">
                      {!a ? (
                        <p className="small muted">Skipped. It's in your reviews.</p>
                      ) : (
                        <>
                          {a.selectedOptionIndex != null && (
                            <p className="small">
                              <span className="muted">Your answer: </span>
                              {optionLetter(a.selectedOptionIndex)}. {it.question.options[a.selectedOptionIndex]}
                            </p>
                          )}
                          {a.answerText && (
                            <p className="small">
                              <span className="muted">Your answer: </span>
                              {a.answerText}
                            </p>
                          )}
                          {a.correctOptionIndex != null && a.correctOptionIndex !== a.selectedOptionIndex && (
                            <p className="small">
                              <span className="muted">Correct: </span>
                              {optionLetter(a.correctOptionIndex)}. {it.question.options[a.correctOptionIndex]}
                            </p>
                          )}
                          {a.referenceAnswer && <Markdown className="small">{`**A strong answer:** ${a.referenceAnswer}`}</Markdown>}
                          {a.explanation && <Markdown className="small text-2">{a.explanation}</Markdown>}
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
    </Card>
  );
}
