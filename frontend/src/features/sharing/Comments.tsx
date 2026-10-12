import { MessageSquare, Send, Trash2 } from "lucide-react";
import { useEffect, useState } from "react";
import { errorMessage } from "../../shared/api/errors";
import { Avatar } from "../../shared/illustrations/Avatar";
import { Emoji } from "../../shared/illustrations/Emoji";
import { formatRelative } from "../../shared/lib/format";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { ConfirmButton } from "../../shared/ui/ConfirmButton";
import { useToast } from "../../shared/ui/Toast";
import { sharingApi } from "./api";
import type { ShareComment } from "./types";

const MAX_LENGTH = 2000;
const PAGE_SIZE = 20;

/** Discussion under a shared document: newest first, the sharer marked, author or sharer can delete. */
export function Comments({ shareId, onCountChange }: { shareId: string; onCountChange: (delta: number) => void }) {
  const toast = useToast();
  const [comments, setComments] = useState<ShareComment[] | null>(null);
  const [total, setTotal] = useState(0);
  const [next, setNext] = useState(0);
  const [draft, setDraft] = useState("");
  const [posting, setPosting] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    sharingApi.comments(shareId, 0, PAGE_SIZE).then(
      (page) => {
        setComments(page.items);
        setTotal(page.totalItems);
        setNext(1);
      },
      (e) => setError(errorMessage(e)),
    );
  }, [shareId]);

  async function post() {
    const body = draft.trim();
    if (!body) return;
    setPosting(true);
    setError(null);
    try {
      const created = await sharingApi.comment(shareId, body);
      setComments((list) => [created, ...(list ?? [])]);
      setTotal((t) => t + 1);
      onCountChange(1);
      setDraft("");
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setPosting(false);
    }
  }

  async function remove(comment: ShareComment) {
    try {
      await sharingApi.deleteComment(shareId, comment.id);
      setComments((list) => list?.filter((c) => c.id !== comment.id) ?? null);
      setTotal((t) => t - 1);
      onCountChange(-1);
    } catch (e) {
      toast.show("error", errorMessage(e));
    }
  }

  async function older() {
    try {
      const page = await sharingApi.comments(shareId, next, PAGE_SIZE);
      setComments((list) => {
        const seen = new Set((list ?? []).map((c) => c.id));
        return [...(list ?? []), ...page.items.filter((c) => !seen.has(c.id))];
      });
      setNext((n) => n + 1);
    } catch (e) {
      toast.show("error", errorMessage(e));
    }
  }

  return (
    <Card id="comments">
      <h2 className="shared-section-title">
        <Emoji name="speech-balloon" size={28} /> Comments {total > 0 && <span className="muted">· {total}</span>}
      </h2>

      <div className="comment-compose">
        <label htmlFor="comment-body" className="visually-hidden">
          Add a comment
        </label>
        <textarea
          id="comment-body"
          className="textarea comment-input"
          placeholder="Was it accurate? Who is it good for? Anything missing?"
          maxLength={MAX_LENGTH}
          value={draft}
          onChange={(e) => setDraft(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) void post();
          }}
        />
        <div className="row-between">
          <span className="xs muted">{draft.length > MAX_LENGTH * 0.8 ? `${draft.length}/${MAX_LENGTH}` : "Be kind and specific."}</span>
          <Button variant="primary" size="sm" icon={<Send size={16} />} loading={posting} disabled={!draft.trim()} onClick={post}>
            Post
          </Button>
        </div>
      </div>
      {error && <Alert>{error}</Alert>}

      {comments === null ? null : comments.length === 0 ? (
        <p className="small muted comment-empty">
          <MessageSquare size={16} aria-hidden /> No comments yet. Imported it? Tell others how it went.
        </p>
      ) : (
        <ul className="comment-list">
          {comments.map((c) => (
            <li key={c.id} className="comment">
              <Avatar seed={c.author} size={36} />
              <div className="comment-main">
                <div className="comment-meta">
                  <strong className="small break">{c.mine ? "You" : c.author}</strong>
                  {c.bySharer && <Badge tone="review">Sharer</Badge>}
                  <span className="xs muted">{formatRelative(c.createdAt)}</span>
                  {c.canDelete && (
                    <span className="comment-delete">
                      <ConfirmButton onConfirm={() => remove(c)} icon={<Trash2 size={14} />} prompt="Delete?" confirmLabel="Delete" ariaLabel="Delete comment">
                        <span className="visually-hidden">Delete</span>
                      </ConfirmButton>
                    </span>
                  )}
                </div>
                <p className="comment-body break">{c.body}</p>
              </div>
            </li>
          ))}
        </ul>
      )}
      {comments !== null && comments.length < total && (
        <Button variant="ghost" size="sm" onClick={older}>
          Show older comments
        </Button>
      )}
    </Card>
  );
}
