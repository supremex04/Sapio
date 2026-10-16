import { Search, UserPlus } from "lucide-react";
import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Avatar } from "../../shared/illustrations/Avatar";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { ConfirmButton } from "../../shared/ui/ConfirmButton";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { formatRelative } from "../../shared/lib/format";
import { classroomApi } from "./api";
import type { InviteOutcome, InviteResult, Member } from "./types";
import "./classroom.css";

const OUTCOME: Record<InviteOutcome, string> = {
  INVITED: "invited",
  ALREADY_INVITED: "already invited",
  ALREADY_A_STUDENT: "already in your class",
  NOT_A_STUDENT_ACCOUNT: "is a teacher account",
  INVALID_EMAIL: "is not an email address",
  YOURSELF: "is your own address",
};

/** Splits pasted addresses (commas, semicolons, spaces or lines). */
export const parseEmails = (text: string) =>
  text
    .split(/[\s,;]+/)
    .map((e) => e.trim())
    .filter(Boolean);

/** The teacher's students: invite by email, find anyone by name, open their profile. */
export function StudentsPage() {
  const [members, setMembers] = useState<Member[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [query, setQuery] = useState("");
  const [emails, setEmails] = useState("");
  const [results, setResults] = useState<InviteResult[] | null>(null);
  const [inviting, setInviting] = useState(false);

  const load = useCallback(() => classroomApi.roster().then(setMembers, (e) => setError(errorMessage(e))), []);
  useEffect(() => {
    void load();
  }, [load]);

  async function invite() {
    const list = parseEmails(emails);
    if (list.length === 0) return;
    setInviting(true);
    setError(null);
    try {
      const r = await classroomApi.invite(list);
      setResults(r);
      setEmails(r.filter((x) => x.outcome === "INVALID_EMAIL").map((x) => x.email).join("\n"));
      await load();
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setInviting(false);
    }
  }

  const shown = useMemo(() => {
    const q = query.trim().toLowerCase();
    return (members ?? []).filter((m) => !q || (m.name ?? "").toLowerCase().includes(q) || m.email.includes(q));
  }, [members, query]);
  const accepted = shown.filter((m) => m.status === "ACCEPTED");
  const pending = shown.filter((m) => m.status !== "ACCEPTED");

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="page-head">
          <h1 className="row">
            <Emoji name="students" size={40} /> Students
          </h1>
          <p className="muted">Invite by email. Students accept from their notifications; you then see their progress on what you share with them.</p>
        </header>
        {error && <Alert>{error}</Alert>}

        <Card>
          <h2 className="card-title">Invite students</h2>
          <label className="field">
            <span className="field-label">Email addresses (separate with commas or new lines)</span>
            <textarea className="input" rows={3} value={emails} onChange={(e) => setEmails(e.target.value)} placeholder="asha@school.org, ben@school.org" />
          </label>
          <div className="row-between" style={{ marginTop: "var(--space-3)" }}>
            <span className="xs muted">Someone without an account yet sees the invitation once they sign up with that address.</span>
            <Button variant="primary" icon={<UserPlus size={16} />} onClick={() => void invite()} loading={inviting} disabled={parseEmails(emails).length === 0}>
              Invite {parseEmails(emails).length > 1 ? `${parseEmails(emails).length} students` : ""}
            </Button>
          </div>
          {results && (
            <ul className="cls-invite-results small">
              {results.map((r) => (
                <li key={r.email} className={r.outcome === "INVITED" ? "is-ok" : "is-skip"}>
                  {r.email} {OUTCOME[r.outcome]}
                </li>
              ))}
            </ul>
          )}
        </Card>

        <Card>
          <div className="row-between">
            <h2 className="card-title">Your class{members ? ` (${members.filter((m) => m.status === "ACCEPTED").length})` : ""}</h2>
            <label className="cls-search">
              <Search size={16} aria-hidden />
              <input value={query} onChange={(e) => setQuery(e.target.value)} placeholder="Find by name or email" aria-label="Find a student" />
            </label>
          </div>
          {!members && <SkeletonText lines={4} />}
          {members && members.length === 0 && (
            <EmptyState title="No students yet" mood="encourage">
              Invite your first students above.
            </EmptyState>
          )}
          {members && members.length > 0 && shown.length === 0 && <p className="muted small">Nobody matches “{query}”.</p>}
          <ul className="cls-list">
            {accepted.map((m) => (
              <li key={m.id}>
                <div className="cls-row">
                  <Link to={`/students/${m.studentId}`} className="cls-row cls-row-link">
                    <Avatar seed={m.studentId ?? m.email} size={36} />
                    <span className="cls-row-main">
                      <strong>{m.name}</strong>
                      <span className="small muted">
                        {m.email} · joined {m.respondedAt ? formatRelative(m.respondedAt) : ""}
                      </span>
                    </span>
                  </Link>
                  <ConfirmButton onConfirm={() => classroomApi.removeMember(m.id).then(load)} prompt={`Remove ${m.name}? What they received stays theirs.`} confirmLabel="Remove">
                    Remove
                  </ConfirmButton>
                </div>
              </li>
            ))}
            {pending.map((m) => (
              <li key={m.id}>
                <div className="cls-row">
                  <Avatar seed={m.email} size={36} />
                  <span className="cls-row-main">
                    <strong>{m.email}</strong>
                    <span className="small muted">invited {formatRelative(m.invitedAt)}</span>
                  </span>
                  <Badge tone={m.status === "DECLINED" ? "danger" : "neutral"}>{m.status === "DECLINED" ? "Declined" : "Invited"}</Badge>
                  <ConfirmButton onConfirm={() => classroomApi.removeMember(m.id).then(load)} prompt="Withdraw this invitation?" confirmLabel="Withdraw">
                    Withdraw
                  </ConfirmButton>
                </div>
              </li>
            ))}
          </ul>
        </Card>
      </div>
    </PageTransition>
  );
}
