import { Plus } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { cx } from "../../shared/ui/cx";
import { Dialog } from "../../shared/ui/Dialog";
import { EmptyState } from "../../shared/ui/EmptyState";
import { Segmented } from "../../shared/ui/Segmented";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { formatPercent, formatRelative } from "../../shared/lib/format";
import { GeneratingState } from "../study/components/GeneratingState";
import { classroomApi } from "./api";
import { ShareDialog } from "./ShareDialog";
import { DocumentCard } from "./StudentProfilePage";
import { scoreTone } from "./TeacherHome";
import type { ClassTestSummary, Difficulty, DocumentProgress, TestFormat } from "./types";
import "./classroom.css";

/** A teacher's two big actions on a ready document, in place of the learner's Learn / Test cards. */
export function TeacherActions({ documentId, onShared }: { documentId: string; onShared: () => void }) {
  const [sharing, setSharing] = useState(false);
  const [creating, setCreating] = useState(false);
  return (
    <div className="hub-modes cls-actions">
      <button type="button" className="card card-interactive cls-action" onClick={() => setSharing(true)}>
        <Emoji name="share" size={52} />
        <span>
          <strong className="cls-action-title">Share with students</strong>
          <span className="small muted">Each student gets their own copy in their library. Follow their progress in the Students tab.</span>
        </span>
      </button>
      <button type="button" className="card card-interactive cls-action" onClick={() => setCreating(true)}>
        <Emoji name="class-test" size={52} />
        <span>
          <strong className="cls-action-title">Create a class test</strong>
          <span className="small muted">Sapi drafts a full test on this document; you review and edit every question, then send it.</span>
        </span>
      </button>
      <ShareDialog documentId={documentId} open={sharing} onClose={() => setSharing(false)} onShared={onShared} />
      {creating && <CreateTestDialog documentId={documentId} onClose={() => setCreating(false)} />}
    </div>
  );
}

const FORMATS: { value: TestFormat; label: string; hint: string }[] = [
  { value: "OBJECTIVE", label: "Objective", hint: "Multiple choice and true / false, graded instantly." },
  { value: "SUBJECTIVE", label: "Subjective", hint: "Written answers of 1-3 sentences, graded by Sapi against your model answers." },
  { value: "MIXED", label: "Both", hint: "A mix of choice and written questions." },
];

export function CreateTestDialog({ documentId, onClose }: { documentId: string; onClose: () => void }) {
  const navigate = useNavigate();
  const [title, setTitle] = useState("");
  const [format, setFormat] = useState<TestFormat>("MIXED");
  const [difficulty, setDifficulty] = useState<Difficulty>("MIXED");
  const [count, setCount] = useState(10);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function create() {
    setBusy(true);
    setError(null);
    try {
      const test = await classroomApi.createTest(documentId, { title, format, difficulty, questionCount: count });
      navigate(`/class-tests/${test.summary.id}`);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <Dialog
      open
      onClose={busy ? () => undefined : onClose}
      title="Create a class test"
      footer={
        !busy && (
          <Button variant="mastery" onClick={() => void create()}>
            Draft {count} questions
          </Button>
        )
      }
    >
      {busy ? (
        <GeneratingState title="Sapi is writing your test" subtitle="Questions across every topic of the document. You can edit all of them next." />
      ) : (
        <div className="stack">
          {error && <Alert>{error}</Alert>}
          <label className="field">
            <span className="field-label">Title</span>
            <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} placeholder="e.g. Unit 1 quiz" maxLength={200} />
          </label>
          <div className="field">
            <span className="field-label">Questions</span>
            <Segmented label="Question format" value={format} onChange={setFormat} options={FORMATS.map((f) => ({ value: f.value, label: f.label }))} />
            <span className="xs muted">{FORMATS.find((f) => f.value === format)!.hint}</span>
          </div>
          <div className="field">
            <span className="field-label">Difficulty</span>
            <Segmented
              label="Difficulty"
              value={difficulty}
              onChange={setDifficulty}
              options={[
                { value: "EASY", label: "Easy" },
                { value: "MIXED", label: "Mixed" },
                { value: "HARD", label: "Hard" },
              ]}
            />
          </div>
          <label className="field">
            <span className="field-label">Number of questions: {count}</span>
            <input type="range" min={3} max={30} value={count} onChange={(e) => setCount(Number(e.target.value))} />
          </label>
          <p className="xs muted">Covers every topic of the document. Nothing is sent until you review it.</p>
        </div>
      )}
    </Dialog>
  );
}

/** The document's students: progress on it, when they last opened it, their tests. */
export function DocumentStudentsTab({ documentId, version }: { documentId: string; version: number }) {
  const [rows, setRows] = useState<DocumentProgress[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    classroomApi.documentStudents(documentId).then(setRows, (e) => setError(errorMessage(e)));
  }, [documentId, version]);

  if (error) return <Alert>{error}</Alert>;
  if (!rows) return <SkeletonText lines={4} />;
  if (rows.length === 0) {
    return (
      <Card>
        <EmptyState title="Not shared yet" mood="encourage">
          Share this document with your students (above) to follow their progress here.
        </EmptyState>
      </Card>
    );
  }
  const opened = rows.filter((r) => r.firstOpenedAt).length;
  return (
    <div className="stack">
      <p className="small muted">
        Shared with {rows.length} · opened by {opened}
      </p>
      {rows.map((r) => (
        <DocumentCard key={r.studentId} progress={r} studentId={r.studentId} heading="student" />
      ))}
    </div>
  );
}

/** The class tests written on this document. */
export function ClassTestsTab({ documentId }: { documentId: string }) {
  const [tests, setTests] = useState<ClassTestSummary[] | null>(null);
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);
  useEffect(() => {
    classroomApi.tests(documentId).then(setTests, (e) => setError(errorMessage(e)));
  }, [documentId]);

  if (error) return <Alert>{error}</Alert>;
  if (!tests) return <SkeletonText lines={3} />;
  return (
    <div className="stack">
      <div className="row-between">
        <span className="small muted">{tests.length ? `${tests.length} class test${tests.length === 1 ? "" : "s"}` : ""}</span>
        <Button size="sm" variant="mastery" icon={<Plus size={14} />} onClick={() => setCreating(true)}>
          New class test
        </Button>
      </div>
      {tests.length === 0 && (
        <Card>
          <EmptyState title="No class tests yet" mood="happy">
            Sapi drafts a full test on this document; you review it and send it to your students.
          </EmptyState>
        </Card>
      )}
      <ul className="hub-list">
        {tests.map((t) => (
          <li key={t.id}>
            <Link to={`/class-tests/${t.id}`} className="hub-row">
              <span className={cx("hub-score", t.averageScore != null && `is-${scoreTone(t.averageScore) === "is-good" ? "success" : scoreTone(t.averageScore) === "is-mid" ? "warning" : "danger"}`)}>
                {t.averageScore == null ? "…" : formatPercent(t.averageScore)}
              </span>
              <span className="hub-row-main">
                <strong>{t.title}</strong>
                <span className="small muted">
                  {t.questionCount} questions · {t.format === "MIXED" ? "mixed" : t.format.toLowerCase()} ·{" "}
                  {t.status === "DRAFT" ? `draft, ${formatRelative(t.createdAt)}` : `${t.completed}/${t.assigned} completed`}
                </span>
              </span>
              <Badge tone={t.status === "DRAFT" ? "neutral" : "mastery"}>{t.status === "DRAFT" ? "Draft" : "Sent"}</Badge>
            </Link>
          </li>
        ))}
      </ul>
      {creating && <CreateTestDialog documentId={documentId} onClose={() => setCreating(false)} />}
    </div>
  );
}
