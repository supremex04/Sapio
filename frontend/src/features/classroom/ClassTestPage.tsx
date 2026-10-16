import { ArrowLeft, Pencil, Plus, Save, Send, Trash2, X } from "lucide-react";
import { useCallback, useEffect, useState, type ReactNode } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { ConfirmButton } from "../../shared/ui/ConfirmButton";
import { cx } from "../../shared/ui/cx";
import { DateTimePicker } from "../../shared/ui/DateTimePicker";
import { Dialog } from "../../shared/ui/Dialog";
import { Markdown } from "../../shared/ui/Markdown";
import { PageTransition } from "../../shared/ui/PageTransition";
import { ProgressBar } from "../../shared/ui/Progress";
import { Skeleton } from "../../shared/ui/Skeleton";
import { Tabs } from "../../shared/ui/Tabs";
import { formatDateTime, formatPercent, formatRelative } from "../../shared/lib/format";
import { useToast } from "../../shared/ui/Toast";
import { classroomApi } from "./api";
import { StudentPicker } from "./StudentPicker";
import { scoreTone } from "./TeacherHome";
import type { ClassQuestion, ClassTest, QuestionInput, QuestionType, ResultStatus, TestResults } from "./types";
import "./classroom.css";

const TYPE_LABEL: Record<QuestionType, string> = {
  MULTIPLE_CHOICE: "Multiple choice",
  TRUE_FALSE: "True / false",
  SHORT_ANSWER: "Written answer",
};

const STATUS_LABEL: Record<ResultStatus, string> = {
  NOT_STARTED: "Not started",
  IN_PROGRESS: "In progress",
  COMPLETED: "Completed",
  WITHDRAWN: "Removed",
};

/** A class test: review and edit a draft, send it, then follow the results. */
export function ClassTestPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const toast = useToast();
  const [test, setTest] = useState<ClassTest | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<"results" | "questions">("results");
  const [sending, setSending] = useState(false);
  const [saving, setSaving] = useState(false);
  // The title and due date are edited here and saved with "Save as draft" (or before sending).
  const [title, setTitle] = useState("");
  const [dueAt, setDueAt] = useState<string | null>(null);

  const load = useCallback(
    () =>
      classroomApi.test(id).then(
        (t) => {
          setTest(t);
          setTitle(t.summary.title);
          setDueAt(t.summary.dueAt);
        },
        (e) => setError(errorMessage(e)),
      ),
    [id],
  );
  useEffect(() => {
    void load();
  }, [load]);

  if (error && !test) return <Alert>{error}</Alert>;
  if (!test) return <Skeleton height={300} />;
  const s = test.summary;
  const draft = s.status === "DRAFT";
  const toClassTests = `/documents/${s.documentId}?tab=class-tests`;
  const metaChanged = title.trim() !== s.title || dueAt !== s.dueAt;

  /** Saves the title and due date; returns false (with the error shown) when that fails. */
  async function saveMeta(): Promise<boolean> {
    if (!title.trim()) {
      setError("A test needs a title.");
      return false;
    }
    if (!metaChanged) return true;
    try {
      setTest(await classroomApi.updateTest(id, title.trim(), dueAt));
      return true;
    } catch (e) {
      setError(errorMessage(e));
      return false;
    }
  }

  async function saveDraft() {
    setSaving(true);
    setError(null);
    if (await saveMeta()) {
      toast.show("success", "Draft saved. Send it whenever it is ready.");
      navigate(toClassTests);
    }
    setSaving(false);
  }

  async function startSending() {
    setError(null);
    if (await saveMeta()) setSending(true);
  }

  return (
    <PageTransition>
      <div className="stack-lg">
        <Link to={toClassTests} className="back-link small">
          <ArrowLeft size={16} /> {s.documentTitle}
        </Link>
        <header className="cls-test-head">
          <div className="stack-sm" style={{ flex: 1 }}>
            <p className="eyebrow">
              Class test · {test.questions.length} questions · {s.format === "MIXED" ? "objective and subjective" : s.format.toLowerCase()} · {s.difficulty.toLowerCase()}
            </p>
            {draft ? (
              <input className="cls-title-input" value={title} onChange={(e) => setTitle(e.target.value)} aria-label="Test title" maxLength={200} />
            ) : (
              <h1 className="cls-title">{s.title}</h1>
            )}
            <div className="cls-due-row small">
              <span className="field-label">Due</span>
              {draft ? (
                <>
                  <DateTimePicker value={dueAt} onChange={setDueAt} placeholder="No due date" min={new Date()} label="Due date" />
                  <span className="xs muted">optional; shown in students' calendars</span>
                </>
              ) : (
                <span>{s.dueAt ? formatDateTime(s.dueAt) : "No due date"}</span>
              )}
            </div>
          </div>
          <div className="stack-sm cls-test-actions">
            <Badge tone={draft ? "neutral" : "mastery"}>{draft ? (metaChanged ? "Draft · unsaved changes" : "Draft") : `Sent ${s.sentAt ? formatRelative(s.sentAt) : ""}`}</Badge>
            {!draft && (
              <Button variant="primary" icon={<Send size={16} />} onClick={() => setSending(true)}>
                Send to more students
              </Button>
            )}
          </div>
        </header>
        {error && <Alert>{error}</Alert>}

        {draft ? (
          <>
            <QuestionEditorList test={test} onChange={setTest} onError={setError} />
            {/* The three things to do with a draft, always at hand. */}
            <div className="cls-draft-bar">
              <ConfirmButton onConfirm={() => classroomApi.deleteTest(id).then(() => navigate(toClassTests))} prompt="Delete this draft and its questions?" confirmLabel="Delete" icon={<Trash2 size={14} />} variant="ghost" size="md">
                Delete draft
              </ConfirmButton>
              <span className="cls-draft-bar-gap" />
              <Button onClick={() => void saveDraft()} loading={saving} icon={<Save size={16} />}>
                Save as draft
              </Button>
              <Button variant="primary" icon={<Send size={16} />} onClick={() => void startSending()} disabled={test.questions.length === 0}>
                Send to students
              </Button>
            </div>
          </>
        ) : (
          <>
            <Tabs
              label="Test sections"
              value={tab}
              onChange={setTab}
              items={[
                { value: "results", label: `Results (${s.completed}/${s.assigned})` },
                { value: "questions", label: "Questions" },
              ]}
            />
            {tab === "results" ? <ResultsView testId={id} /> : <QuestionList questions={test.questions} />}
          </>
        )}
      </div>
      {sending && (
        <SendDialog
          testId={id}
          dueAt={draft ? dueAt : s.dueAt}
          onClose={() => setSending(false)}
          onSent={(count) => {
            setSending(false);
            setTab("results");
            toast.show("success", `Sent to ${count} student${count === 1 ? "" : "s"}`);
            void load();
          }}
        />
      )}
    </PageTransition>
  );
}

// ---------------------------------------------------------------------------------------------------- editing

function QuestionEditorList({ test, onChange, onError }: { test: ClassTest; onChange: (t: ClassTest) => void; onError: (e: string | null) => void }) {
  const [editing, setEditing] = useState<string | "new" | null>(null);
  const id = test.summary.id;

  async function run(action: () => Promise<ClassTest>) {
    onError(null);
    try {
      onChange(await action());
      setEditing(null);
    } catch (e) {
      onError(errorMessage(e));
    }
  }

  return (
    <div className="stack">
      <p className="small muted">Review every question: edit the wording, fix the answer, delete what you do not want, or add your own. Nothing reaches students until you send it.</p>
      {test.questions.map((q) =>
        editing === q.id ? (
          <QuestionForm key={q.id} topics={test.topics} initial={q} onCancel={() => setEditing(null)} onSave={(input) => run(() => classroomApi.updateQuestion(id, q.id, input))} />
        ) : (
          <QuestionCard
            key={q.id}
            question={q}
            actions={
              <span className="row">
                <Button size="sm" variant="ghost" icon={<Pencil size={14} />} onClick={() => setEditing(q.id)}>
                  Edit
                </Button>
                <Button size="sm" variant="ghost" icon={<Trash2 size={14} />} onClick={() => void run(() => classroomApi.deleteQuestion(id, q.id))} aria-label={`Delete question ${q.position}`}>
                  Delete
                </Button>
              </span>
            }
          />
        ),
      )}
      {editing === "new" ? (
        <QuestionForm topics={test.topics} initial={null} onCancel={() => setEditing(null)} onSave={(input) => run(() => classroomApi.addQuestion(id, input))} />
      ) : (
        <Button icon={<Plus size={16} />} onClick={() => setEditing("new")}>
          Add a question
        </Button>
      )}
    </div>
  );
}

function QuestionCard({ question: q, actions }: { question: ClassQuestion; actions?: ReactNode }) {
  return (
    <Card>
      <div className="row-between">
        <span className="small muted">
          <strong>Q{q.position}</strong> · {TYPE_LABEL[q.type]} · {q.topicTitle}
        </span>
        {actions}
      </div>
      <div className="cls-q-prompt">
        <Markdown>{q.prompt}</Markdown>
      </div>
      {q.type === "SHORT_ANSWER" ? (
        <p className="small">
          <span className="muted">Model answer:</span> {q.referenceAnswer}
        </p>
      ) : (
        <ol className="cls-options">
          {q.options.map((o, i) => (
            <li key={i} className={cx(i === q.correctOption && "is-correct")}>
              {o}
              {i === q.correctOption && <span className="xs"> · correct</span>}
            </li>
          ))}
        </ol>
      )}
      {q.explanation && <p className="xs muted">Why: {q.explanation}</p>}
    </Card>
  );
}

function QuestionList({ questions }: { questions: ClassQuestion[] }) {
  return (
    <div className="stack">
      {questions.map((q) => (
        <QuestionCard key={q.id} question={q} />
      ))}
    </div>
  );
}

const blank = (topicPosition: number): QuestionInput => ({
  topicPosition,
  type: "MULTIPLE_CHOICE",
  prompt: "",
  options: ["", "", "", ""],
  correctOption: 0,
  referenceAnswer: null,
  explanation: "",
});

function QuestionForm({
  topics,
  initial,
  onCancel,
  onSave,
}: {
  topics: { position: number; title: string }[];
  initial: ClassQuestion | null;
  onCancel: () => void;
  onSave: (q: QuestionInput) => Promise<void>;
}) {
  const [q, setQ] = useState<QuestionInput>(() => (initial ? { ...initial, explanation: initial.explanation ?? "" } : blank(topics[0]?.position ?? 1)));
  const [busy, setBusy] = useState(false);
  const set = (patch: Partial<QuestionInput>) => setQ((x) => ({ ...x, ...patch }));

  function changeType(type: QuestionType) {
    if (type === "TRUE_FALSE") set({ type, options: ["True", "False"], correctOption: 0, referenceAnswer: null });
    else if (type === "SHORT_ANSWER") set({ type, options: [], correctOption: null, referenceAnswer: q.referenceAnswer ?? "" });
    else set({ type, options: q.options.length >= 2 && q.type === "MULTIPLE_CHOICE" ? q.options : ["", "", "", ""], correctOption: 0, referenceAnswer: null });
  }

  async function save() {
    setBusy(true);
    try {
      await onSave(q);
    } finally {
      setBusy(false);
    }
  }

  return (
    <Card className="cls-q-form">
      <div className="cls-form-row">
        <label className="field">
          <span className="field-label">Topic</span>
          <select className="input" value={q.topicPosition} onChange={(e) => set({ topicPosition: Number(e.target.value) })}>
            {topics.map((t) => (
              <option key={t.position} value={t.position}>
                {t.title}
              </option>
            ))}
          </select>
        </label>
        <label className="field">
          <span className="field-label">Type</span>
          <select className="input" value={q.type} onChange={(e) => changeType(e.target.value as QuestionType)}>
            {(Object.keys(TYPE_LABEL) as QuestionType[]).map((t) => (
              <option key={t} value={t}>
                {TYPE_LABEL[t]}
              </option>
            ))}
          </select>
        </label>
      </div>
      <label className="field">
        <span className="field-label">Question</span>
        <textarea className="input" rows={3} value={q.prompt} onChange={(e) => set({ prompt: e.target.value })} maxLength={2000} autoFocus />
      </label>
      {q.type === "SHORT_ANSWER" ? (
        <label className="field">
          <span className="field-label">Model answer (Sapi grades written answers against it)</span>
          <textarea className="input" rows={2} value={q.referenceAnswer ?? ""} onChange={(e) => set({ referenceAnswer: e.target.value })} />
        </label>
      ) : (
        <fieldset className="cls-option-edit">
          <legend className="field-label">Options (choose the correct one)</legend>
          {q.options.map((o, i) => (
            <div key={i} className="row">
              <input type="radio" name="correct" checked={q.correctOption === i} onChange={() => set({ correctOption: i })} aria-label={`Option ${i + 1} is correct`} />
              <input
                className="input"
                value={o}
                disabled={q.type === "TRUE_FALSE"}
                onChange={(e) => set({ options: q.options.map((x, j) => (j === i ? e.target.value : x)) })}
                placeholder={`Option ${i + 1}`}
              />
              {q.type === "MULTIPLE_CHOICE" && q.options.length > 2 && (
                <button
                  type="button"
                  className="cal-step"
                  aria-label={`Remove option ${i + 1}`}
                  onClick={() => set({ options: q.options.filter((_, j) => j !== i), correctOption: q.correctOption === i ? 0 : (q.correctOption ?? 0) > i ? (q.correctOption ?? 0) - 1 : q.correctOption })}
                >
                  <X size={14} />
                </button>
              )}
            </div>
          ))}
          {q.type === "MULTIPLE_CHOICE" && q.options.length < 6 && (
            <Button size="sm" variant="ghost" icon={<Plus size={14} />} onClick={() => set({ options: [...q.options, ""] })}>
              Add option
            </Button>
          )}
        </fieldset>
      )}
      <label className="field">
        <span className="field-label">Explanation (shown in the student's report)</span>
        <textarea className="input" rows={2} value={q.explanation} onChange={(e) => set({ explanation: e.target.value })} />
      </label>
      <div className="row" style={{ justifyContent: "flex-end" }}>
        <Button variant="ghost" onClick={onCancel}>
          Cancel
        </Button>
        <Button variant="primary" onClick={() => void save()} loading={busy}>
          {initial ? "Save question" : "Add question"}
        </Button>
      </div>
    </Card>
  );
}

// ---------------------------------------------------------------------------------------------------- sending

function SendDialog({ testId, dueAt, onClose, onSent }: { testId: string; dueAt: string | null; onClose: () => void; onSent: (count: number) => void }) {
  const [selection, setSelection] = useState<{ ids: string[]; count: number }>({ ids: [], count: 0 });
  const [done, setDone] = useState<string[]>([]);
  const [due, setDue] = useState<string | null>(dueAt);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    classroomApi.results(testId).then((r) => setDone(r.students.filter((x) => x.assessmentId).map((x) => x.studentId)), () => setDone([]));
  }, [testId]);

  async function send() {
    setBusy(true);
    setError(null);
    try {
      const { sent } = await classroomApi.send(testId, selection.ids, due);
      onSent(sent);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title="Send the test"
      footer={
        <Button variant="primary" icon={<Send size={16} />} onClick={() => void send()} loading={busy} disabled={selection.count === 0}>
          Send{selection.count ? ` to ${selection.count}` : ""}
        </Button>
      }
    >
      <div className="stack">
        {error && <Alert>{error}</Alert>}
        <p className="small muted">Students without the document get it too. They find the test in their notifications and calendar; once sent, its questions cannot change.</p>
        <StudentPicker done={done} onChange={(ids, count) => setSelection({ ids, count })} />
        <div className="field">
          <span className="field-label">Due (optional)</span>
          <DateTimePicker value={due} onChange={setDue} placeholder="No due date" min={new Date()} label="Due date" />
        </div>
      </div>
    </Dialog>
  );
}

// ---------------------------------------------------------------------------------------------------- results

function ResultsView({ testId }: { testId: string }) {
  const [results, setResults] = useState<TestResults | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    classroomApi.results(testId).then(setResults, (e) => setError(errorMessage(e)));
  }, [testId]);

  if (error) return <Alert>{error}</Alert>;
  if (!results) return <Skeleton height={200} />;
  const completed = results.students.filter((s) => s.status === "COMPLETED").length;
  return (
    <div className="stack">
      <div className="cls-scores card">
        <div className="cls-score-block">
          <span className="cls-score-big">
            {completed}/{results.students.length}
          </span>
          <span className="small muted">Completed</span>
        </div>
        {(["averageScore", "lowestScore", "highestScore"] as const).map((k) => (
          <div key={k} className="cls-score-block">
            <span className={cx("cls-score-big", scoreTone(results[k]))}>{results[k] == null ? "–" : formatPercent(results[k])}</span>
            <span className="small muted">{k === "averageScore" ? "Average" : k === "lowestScore" ? "Lowest" : "Highest"}</span>
          </div>
        ))}
      </div>

      {results.topics.length > 0 && (
        <Card>
          <h2 className="card-title">By topic (class average, weakest first)</h2>
          <ul className="cls-topic-results">
            {results.topics.map((t) => (
              <li key={t.title}>
                <div className="row-between small">
                  <strong>{t.title}</strong>
                  <span className={cx(scoreTone(t.averageScore))}>
                    {formatPercent(t.averageScore)} · {t.students} student{t.students === 1 ? "" : "s"}
                  </span>
                </div>
                <ProgressBar value={t.averageScore} size="sm" label={`${t.title} average`} color={t.averageScore >= 0.8 ? "var(--green-main)" : t.averageScore >= 0.5 ? "var(--amber-main)" : "var(--red-main)"} />
              </li>
            ))}
          </ul>
        </Card>
      )}

      <Card>
        <h2 className="card-title">Students</h2>
        <ul className="cls-list">
          {results.students.map((st) => {
            const row = (
              <>
                <span className={cx("cls-score", scoreTone(st.score))}>{st.score == null ? "…" : formatPercent(st.score)}</span>
                <span className="cls-row-main">
                  <strong>{st.name}</strong>
                  <span className="small muted">
                    {STATUS_LABEL[st.status]}
                    {st.status === "IN_PROGRESS" && ` · ${st.answered}/${st.questionCount} answered`}
                    {st.completedAt && ` · ${formatRelative(st.completedAt)}`}
                  </span>
                </span>
              </>
            );
            return (
              <li key={st.studentId}>
                {st.assessmentId ? (
                  <Link to={`/students/${st.studentId}/tests/${st.assessmentId}`} className="cls-row">
                    {row}
                  </Link>
                ) : (
                  <div className="cls-row">{row}</div>
                )}
              </li>
            );
          })}
        </ul>
      </Card>
    </div>
  );
}
