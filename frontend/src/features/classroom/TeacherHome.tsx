import { ArrowRight } from "lucide-react";
import { useEffect, useState } from "react";
import { Link } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Emoji, type IconName } from "../../shared/illustrations/Emoji";
import { Sage } from "../../shared/illustrations/Sage";
import { Alert } from "../../shared/ui/Alert";
import { ButtonLink } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { cx } from "../../shared/ui/cx";
import { PageTransition } from "../../shared/ui/PageTransition";
import { Skeleton } from "../../shared/ui/Skeleton";
import { firstName, formatPercent, formatRelative, greeting } from "../../shared/lib/format";
import { useAuth } from "../auth/AuthContext";
import { classroomApi } from "./api";
import type { ClassOverview } from "./types";
import "./classroom.css";

export const scoreTone = (score: number | null) => (score == null ? "" : score >= 0.8 ? "is-good" : score >= 0.5 ? "is-mid" : "is-low");

/** The teacher's home: the class at a glance, who needs help, and the latest results. */
export function TeacherHome() {
  const { user } = useAuth();
  const [overview, setOverview] = useState<ClassOverview | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    classroomApi.overview().then(setOverview, (e) => setError(errorMessage(e)));
  }, []);

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="page-head">
          <h1>
            {greeting()}, {firstName(user?.fullName)}!
          </h1>
          <p className="muted">Your class at a glance.</p>
        </header>
        {error && <Alert>{error}</Alert>}
        {!overview && !error && <Skeleton height={220} />}
        {overview && overview.students === 0 && overview.pendingInvites === 0 && <FirstSteps />}
        {overview && (overview.students > 0 || overview.pendingInvites > 0) && (
          <>
            <div className="cls-stats">
              <Stat icon="students" value={overview.students} label="Students" sub={overview.pendingInvites ? `${overview.pendingInvites} invited` : undefined} to="/students" />
              <Stat icon="share" value={overview.documentsShared} label="Documents shared" to="/library" />
              <Stat icon="class-test" value={overview.testsSent} label="Class tests sent" />
              <Stat icon="insights" value={overview.testsCompleted} label="Class tests completed" />
            </div>

            <Card>
              <h2 className="card-title">Test scores</h2>
              {overview.testsCompleted === 0 ? (
                <p className="muted small">No completed class tests yet. Send one from a document to see how your class does.</p>
              ) : (
                <div className="cls-scores">
                  <Score label="Average" value={overview.averageScore} />
                  <Score label="Lowest" value={overview.lowestScore} />
                  <Score label="Highest" value={overview.highestScore} />
                </div>
              )}
            </Card>

            <div className="cls-two">
              <Card>
                <h2 className="card-title">Needs attention</h2>
                {overview.needsAttention.length === 0 ? (
                  <p className="muted small">Nobody is struggling right now. Students averaging under 50% on their tests show up here.</p>
                ) : (
                  <ul className="cls-list">
                    {overview.needsAttention.map((a) => (
                      <li key={a.studentId}>
                        <Link to={`/students/${a.studentId}`} className="cls-row">
                          <span className="cls-row-main">
                            <strong>{a.name}</strong>
                            <span className="small muted">{a.reason}</span>
                            {a.topics.length > 0 && <span className="small">Weak: {a.topics.join(", ")}</span>}
                          </span>
                          <ArrowRight size={16} aria-hidden />
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
              <Card>
                <h2 className="card-title">Latest results</h2>
                {overview.recent.length === 0 ? (
                  <p className="muted small">Completed tests appear here.</p>
                ) : (
                  <ul className="cls-list">
                    {overview.recent.map((r) => (
                      <li key={r.assessmentId}>
                        <Link to={`/students/${r.studentId}/tests/${r.assessmentId}`} className="cls-row">
                          <span className={cx("cls-score", scoreTone(r.score))}>{formatPercent(r.score)}</span>
                          <span className="cls-row-main">
                            <strong>{r.studentName}</strong>
                            <span className="small muted">
                              {r.testTitle ?? "Class test"} · {r.documentTitle} · {formatRelative(r.completedAt)}
                            </span>
                          </span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                )}
              </Card>
            </div>
          </>
        )}
      </div>
    </PageTransition>
  );
}

function FirstSteps() {
  return (
    <Card className="cls-first">
      <Sage mood="happy" size={96} />
      <div className="stack-sm">
        <h2>Set up your class in three steps</h2>
        <ol className="cls-steps">
          <li>
            <strong>Invite your students</strong> by email. They accept from their notifications.
          </li>
          <li>
            <strong>Upload a document</strong> and share it: each student gets it in their library.
          </li>
          <li>
            <strong>Send a class test</strong>, then follow progress and results here.
          </li>
        </ol>
        <div className="row">
          <ButtonLink to="/students" variant="primary">
            Invite students
          </ButtonLink>
          <ButtonLink to="/library">Upload a document</ButtonLink>
        </div>
      </div>
    </Card>
  );
}

function Stat({ icon, value, label, sub, to }: { icon: IconName; value: number; label: string; sub?: string; to?: string }) {
  const body = (
    <>
      <Emoji name={icon} size={36} />
      <span>
        <span className="cls-stat-value">{value}</span>
        <span className="small muted cls-stat-label">{label}</span>
        {sub && <span className="xs muted">{sub}</span>}
      </span>
    </>
  );
  return to ? (
    <Link to={to} className="card card-tight card-interactive cls-stat">
      {body}
    </Link>
  ) : (
    <div className="card card-tight cls-stat">{body}</div>
  );
}

function Score({ label, value }: { label: string; value: number | null }) {
  return (
    <div className="cls-score-block">
      <span className={cx("cls-score-big", scoreTone(value))}>{value == null ? "–" : formatPercent(value)}</span>
      <span className="small muted">{label}</span>
    </div>
  );
}
