import { Send, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { formatRelative } from "../../shared/lib/format";
import { classroomApi } from "./api";
import type { Notice } from "./types";
import { useIsTeacher } from "./useRole";
import "./classroom.css";

/** Notices: a teacher writes to the whole class (each student is notified); students read their teachers' notices. */
export function NoticesPage() {
  const teacher = useIsTeacher();
  const [notices, setNotices] = useState<Notice[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [title, setTitle] = useState("");
  const [body, setBody] = useState("");
  const [posting, setPosting] = useState(false);

  const load = useCallback(() => classroomApi.notices().then(setNotices, (e) => setError(errorMessage(e))), []);
  useEffect(() => {
    void load();
  }, [load]);

  async function post() {
    setPosting(true);
    setError(null);
    try {
      await classroomApi.postNotice(title, body);
      setTitle("");
      setBody("");
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPosting(false);
    }
  }

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="page-head">
          <h1 className="row">
            <Emoji name="notice" size={40} /> Notices
          </h1>
          <p className="muted">{teacher ? "Messages to your whole class. Every student is notified." : "Messages from your teachers."}</p>
        </header>
        {error && <Alert>{error}</Alert>}
        {teacher && (
          <Card>
            <h2 className="card-title">New notice</h2>
            <div className="stack">
              <label className="field">
                <span className="field-label">Title</span>
                <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} placeholder="e.g. Lab moved to room 4" />
              </label>
              <label className="field">
                <span className="field-label">Message</span>
                <textarea className="input" rows={4} value={body} onChange={(e) => setBody(e.target.value)} maxLength={5000} />
              </label>
              <div className="row" style={{ justifyContent: "flex-end" }}>
                <Button variant="primary" icon={<Send size={16} />} onClick={() => void post()} loading={posting} disabled={!title.trim() || !body.trim()}>
                  Send to class
                </Button>
              </div>
            </div>
          </Card>
        )}
        {!notices && <SkeletonText lines={4} />}
        {notices && notices.length === 0 && (
          <Card>
            <EmptyState title="No notices yet" mood="happy">
              {teacher ? "Your first notice goes to every student in your class." : "When your teacher posts a notice, it shows up here and in your notifications."}
            </EmptyState>
          </Card>
        )}
        <ul className="stack">
          {notices?.map((n) => (
            <li key={n.id}>
              <Card>
                <div className="row-between">
                  <h2 className="card-title">{n.title}</h2>
                  {teacher && (
                    <Button size="sm" variant="ghost" icon={<Trash2 size={14} />} onClick={() => void classroomApi.deleteNotice(n.id).then(load)} aria-label="Delete notice">
                      Delete
                    </Button>
                  )}
                </div>
                <p className="cls-notice-body">{n.body}</p>
                <p className="xs muted">
                  {teacher ? "Sent" : n.teacherName} · {formatRelative(n.createdAt)}
                </p>
              </Card>
            </li>
          ))}
        </ul>
      </div>
    </PageTransition>
  );
}
