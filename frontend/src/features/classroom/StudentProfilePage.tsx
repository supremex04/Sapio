import { ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Avatar } from "../../shared/illustrations/Avatar";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Card } from "../../shared/ui/Card";
import { cx } from "../../shared/ui/cx";
import { PageTransition } from "../../shared/ui/PageTransition";
import { ProgressBar } from "../../shared/ui/Progress";
import { Skeleton } from "../../shared/ui/Skeleton";
import { formatPercent, formatRelative } from "../../shared/lib/format";
import { AssessmentReportView } from "../study/assessment/AssessmentReport";
import type { Assessment } from "../study/types";
import { classroomApi } from "./api";
import { scoreTone } from "./TeacherHome";
import type { DocumentProgress, StudentProfile } from "./types";
import "./classroom.css";

/** One student, as their teacher sees them: progress on each shared document, test results, what to work on. */
export function StudentProfilePage() {
  const { studentId = "" } = useParams();
  const [profile, setProfile] = useState<StudentProfile | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    classroomApi.student(studentId).then(setProfile, (e) => setError(errorMessage(e)));
  }, [studentId]);

  return (
    <PageTransition>
      <div className="stack-lg">
        <Link to="/students" className="back-link small">
          <ArrowLeft size={16} /> Students
        </Link>
        {error && <Alert>{error}</Alert>}
        {!profile && !error && <Skeleton height={240} />}
        {profile && (
          <>
            <header className="cls-profile-head">
              <Avatar seed={profile.id} size={64} />
              <div>
                <h1>{profile.name}</h1>
                <p className="muted small">
                  {profile.email}
                  {profile.joinedAt && ` · joined ${formatRelative(profile.joinedAt)}`}
                </p>
              </div>
            </header>
            <div className="cls-stats">
              <div className="card card-tight cls-stat">
                <Emoji name="share" size={32} />
                <span>
                  <span className="cls-stat-value">{profile.documents.length}</span>
                  <span className="small muted cls-stat-label">Documents from you</span>
                </span>
              </div>
              <div className="card card-tight cls-stat">
                <Emoji name="class-test" size={32} />
                <span>
                  <span className="cls-stat-value">{profile.testsCompleted}</span>
                  <span className="small muted cls-stat-label">Tests completed</span>
                </span>
              </div>
              <div className="card card-tight cls-stat">
                <Emoji name="insights" size={32} />
                <span>
                  <span className={cx("cls-stat-value", scoreTone(profile.averageScore))}>{profile.averageScore == null ? "–" : formatPercent(profile.averageScore)}</span>
                  <span className="small muted cls-stat-label">Average score</span>
                </span>
              </div>
            </div>

            <Card tone={profile.needsWork.length ? "amber" : undefined}>
              <h2 className="card-title">Topics to work on</h2>
              {profile.needsWork.length === 0 ? (
                <p className="small muted">Nothing flagged. Topics show up here when practice accuracy stays under 50% or a test rates them weak.</p>
              ) : (
                <ul className="cls-weak">
                  {profile.needsWork.map((w, i) => (
                    <li key={i}>
                      <strong>{w.topic}</strong>
                      <span className="small muted">
                        {w.documentTitle} · {w.reason}
                        {w.score != null && ` (${formatPercent(w.score)})`}
                      </span>
                    </li>
                  ))}
                </ul>
              )}
            </Card>

            {profile.documents.length === 0 && <p className="muted">You have not shared a document with {profile.name} yet.</p>}
            {profile.documents.map((d) => (
              <DocumentCard key={d.sourceDocumentId} progress={d} studentId={profile.id} />
            ))}
          </>
        )}
      </div>
    </PageTransition>
  );
}

/** Progress on one document: topics done, mastery per topic, tests. Also used on the document's Students tab. */
export function DocumentCard({ progress: d, studentId, heading = "document" }: { progress: DocumentProgress; studentId: string; heading?: "document" | "student" }) {
  return (
    <Card>
      <div className="row-between">
        <h2 className="card-title">{heading === "document" ? d.documentTitle : <Link to={`/students/${studentId}`}>{d.studentName}</Link>}</h2>
        <span className="xs muted">
          {d.removedByStudent ? "Removed from their library" : d.lastOpenedAt ? `Opened ${formatRelative(d.lastOpenedAt)}` : "Not opened yet"}
        </span>
      </div>
      {!d.removedByStudent && (
        <div className="stack-sm">
          <div className="row-between small">
            <span>
              {d.completedTopics} of {d.topicCount} topics done
            </span>
            <span className="muted">{d.averageMastery == null ? "No practice yet" : `Mastery ${formatPercent(d.averageMastery)}`}</span>
          </div>
          <ProgressBar value={d.topicCount ? d.completedTopics / d.topicCount : 0} label="Topics done" />
          <ul className="cls-topics">
            {d.topics.map((t) => (
              <li key={t.position} className={cx("cls-topic", t.mastery == null ? "is-none" : t.mastery >= 0.8 ? "is-good" : t.mastery >= 0.5 ? "is-mid" : "is-low")} title={t.mastery == null ? "Not practised yet" : `Mastery ${formatPercent(t.mastery)}`}>
                {t.title}
                {t.status === "COMPLETED" && " · done"}
              </li>
            ))}
          </ul>
          {d.tests.length > 0 && (
            <ul className="cls-list">
              {d.tests.map((t) => (
                <li key={t.assessmentId}>
                  <Link to={`/students/${studentId}/tests/${t.assessmentId}`} className="cls-row">
                    <span className={cx("cls-score", scoreTone(t.score))}>{t.score == null ? "…" : formatPercent(t.score)}</span>
                    <span className="cls-row-main">
                      <strong>{t.title}</strong>
                      <span className="small muted">{t.status === "COMPLETED" ? `Finished ${formatRelative(t.completedAt ?? t.createdAt)}` : "In progress"}</span>
                    </span>
                    {t.assigned && <Badge tone="mastery">Your test</Badge>}
                  </Link>
                </li>
              ))}
            </ul>
          )}
        </div>
      )}
    </Card>
  );
}

/** A student's test report, read-only for their teacher. */
export function StudentReportPage() {
  const { studentId = "", assessmentId = "" } = useParams();
  const [assessment, setAssessment] = useState<Assessment | null>(null);
  const [name, setName] = useState("");
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    classroomApi.studentAssessment(assessmentId).then(setAssessment, (e) => setError(errorMessage(e)));
    classroomApi.student(studentId).then((p) => setName(p.name), () => undefined);
  }, [studentId, assessmentId]);

  if (error) return <Alert>{error}</Alert>;
  if (!assessment) return <Skeleton height={300} />;
  if (!assessment.report) {
    return (
      <div className="stack">
        <Link to={`/students/${studentId}`} className="back-link small">
          <ArrowLeft size={16} /> {name || "Student"}
        </Link>
        <Card>
          <h1 className="card-title">{assessment.assignment?.title ?? "Practice test"}</h1>
          <p className="muted">
            In progress: {assessment.answeredCount} of {assessment.questionCount} answered. The report appears once {name || "the student"} finishes.
          </p>
        </Card>
      </div>
    );
  }
  return <AssessmentReportView assessment={assessment} fresh={false} observer={{ studentName: name, exitTo: `/students/${studentId}` }} />;
}
