import { Bell, Check, X } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { usePolling } from "../../shared/hooks/usePolling";
import { Emoji, type IconName } from "../../shared/illustrations/Emoji";
import { Button } from "../../shared/ui/Button";
import { cx } from "../../shared/ui/cx";
import { formatRelative } from "../../shared/lib/format";
import { classroomApi } from "./api";
import type { AppNotification, Inbox, Invitation, NotificationType } from "./types";
import { useIsTeacher } from "./useRole";
import "./classroom.css";

const ICON: Record<NotificationType, IconName> = {
  CLASS_INVITE: "invite",
  INVITE_ANSWERED: "students",
  DOCUMENT_SHARED: "share",
  TEST_ASSIGNED: "class-test",
  NOTICE: "notice",
  EVENT: "calendar",
};

/**
 * The inbox: invitations to answer (students) on top, then recent notifications. The badge counts both; opening
 * the panel marks notifications read. Checked every minute and when the window regains focus.
 */
/** ``placement``: down (right-aligned, top bar), start (left-aligned, sidebar), right (beside a collapsed sidebar). */
export function NotificationsButton({ placement = "down" }: { placement?: "down" | "start" | "right" }) {
  const teacher = useIsTeacher();
  const navigate = useNavigate();
  const [open, setOpen] = useState(false);
  const [inbox, setInbox] = useState<Inbox | null>(null);
  const [invitations, setInvitations] = useState<Invitation[]>([]);
  const [error, setError] = useState<string | null>(null);
  const ref = useRef<HTMLDivElement>(null);

  const load = useCallback(async () => {
    try {
      const [i, inv] = await Promise.all([classroomApi.inbox(), teacher ? Promise.resolve([]) : classroomApi.invitations()]);
      setInbox(i);
      setInvitations(inv);
    } catch {
      /* the badge simply stays as it was */
    }
  }, [teacher]);

  useEffect(() => {
    void load();
    const onFocus = () => void load();
    window.addEventListener("focus", onFocus);
    return () => window.removeEventListener("focus", onFocus);
  }, [load]);
  usePolling(load, 60_000, true);

  useEffect(() => {
    if (!open) return;
    const away = (e: PointerEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const key = (e: KeyboardEvent) => e.key === "Escape" && setOpen(false);
    document.addEventListener("pointerdown", away);
    document.addEventListener("keydown", key);
    return () => {
      document.removeEventListener("pointerdown", away);
      document.removeEventListener("keydown", key);
    };
  }, [open]);

  function toggle() {
    const next = !open;
    setOpen(next);
    if (next && inbox && inbox.unread > 0) {
      void classroomApi.markRead().then(() => setInbox((i) => (i ? { ...i, unread: 0 } : i)));
    }
  }

  async function answer(inv: Invitation, accept: boolean) {
    setError(null);
    try {
      await (accept ? classroomApi.accept(inv.id) : classroomApi.decline(inv.id));
      await load();
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  function follow(n: AppNotification) {
    if (!n.link) return;
    setOpen(false);
    navigate(n.link);
  }

  const count = (inbox?.unread ?? 0) + invitations.length;
  return (
    <div className="inbox" ref={ref}>
      <button type="button" className="inbox-button" aria-haspopup="dialog" aria-expanded={open} onClick={toggle} aria-label={count ? `Notifications, ${count} new` : "Notifications"}>
        <Bell size={20} strokeWidth={2.4} aria-hidden />
        {count > 0 && <span className="inbox-count">{count > 9 ? "9+" : count}</span>}
      </button>
      {open && (
        <div className={cx("inbox-panel", placement === "right" && "inbox-panel-right", placement === "start" && "inbox-panel-start")} role="dialog" aria-label="Notifications">
          <header className="inbox-head">
            <strong>Notifications</strong>
          </header>
          {error && <p className="small inbox-error">{error}</p>}
          {invitations.map((inv) => (
            <div key={inv.id} className="inbox-invite">
              <Emoji name="invite" size={28} />
              <div className="inbox-text">
                <strong>{inv.teacherName} invited you to their class</strong>
                <span className="xs muted">
                  {inv.teacherEmail} · {formatRelative(inv.invitedAt)}
                </span>
                <span className="xs muted">They will see your progress on the documents and tests they give you, nothing else.</span>
                <span className="row inbox-actions">
                  <Button size="sm" variant="primary" icon={<Check size={14} />} onClick={() => void answer(inv, true)}>
                    Accept
                  </Button>
                  <Button size="sm" variant="ghost" icon={<X size={14} />} onClick={() => void answer(inv, false)}>
                    Decline
                  </Button>
                </span>
              </div>
            </div>
          ))}
          {inbox && inbox.items.length === 0 && invitations.length === 0 && <p className="small muted inbox-empty">Nothing new. You are all caught up.</p>}
          <ul className="inbox-list">
            {inbox?.items
              .filter((n) => n.type !== "CLASS_INVITE" || invitations.length === 0)
              .map((n) => (
                <li key={n.id}>
                  <button type="button" className={cx("inbox-item", !n.readAt && "is-unread", !n.link && "is-static")} onClick={() => follow(n)}>
                    <Emoji name={ICON[n.type]} size={26} />
                    <span className="inbox-text">
                      <strong>{n.title}</strong>
                      {n.body && <span className="small inbox-body">{n.body}</span>}
                      <span className="xs muted">{formatRelative(n.createdAt)}</span>
                    </span>
                  </button>
                </li>
              ))}
          </ul>
        </div>
      )}
    </div>
  );
}
