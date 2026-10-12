import { ArrowBigDown, ArrowBigUp } from "lucide-react";
import { useState } from "react";
import { errorMessage } from "../../shared/api/errors";
import { cx } from "../../shared/ui/cx";
import { useToast } from "../../shared/ui/Toast";
import { sharingApi } from "./api";
import type { VoteValue, Votes } from "./types";

/** Applies a vote change locally (optimistic UI); the server answer replaces it. */
function apply(v: Votes, next: VoteValue): Votes {
  const up = v.upvotes - (v.myVote === 1 ? 1 : 0) + (next === 1 ? 1 : 0);
  const down = v.downvotes - (v.myVote === -1 ? 1 : 0) + (next === -1 ? 1 : 0);
  return { upvotes: up, downvotes: down, myVote: next };
}

/**
 * Up / down vote. Pressing your current vote takes it back. Read-only (counts only) on your own share.
 */
export function VoteControl({ shareId, votes, readOnly, size = "sm", onChange }: {
  shareId: string;
  votes: Votes;
  readOnly?: boolean;
  size?: "sm" | "lg";
  onChange: (votes: Votes) => void;
}) {
  const toast = useToast();
  const [busy, setBusy] = useState(false);

  async function cast(value: 1 | -1) {
    if (busy) return;
    const next: VoteValue = votes.myVote === value ? 0 : value;
    const before = votes;
    onChange(apply(votes, next));
    setBusy(true);
    try {
      onChange(await sharingApi.vote(shareId, next));
    } catch (e) {
      onChange(before);
      toast.show("error", errorMessage(e));
    } finally {
      setBusy(false);
    }
  }

  const icon = size === "lg" ? 22 : 18;
  return (
    <div className={cx("vote", `vote-${size}`)} role="group" aria-label="Rate this document">
      <button
        type="button"
        className={cx("vote-btn", "is-up", votes.myVote === 1 && "is-active")}
        aria-pressed={votes.myVote === 1}
        aria-label={`Helpful (${votes.upvotes})`}
        title={readOnly ? "You can't rate your own share" : "Helpful"}
        disabled={readOnly}
        onClick={() => cast(1)}
      >
        <ArrowBigUp size={icon} fill={votes.myVote === 1 ? "currentColor" : "none"} aria-hidden />
        <span>{votes.upvotes}</span>
      </button>
      <button
        type="button"
        className={cx("vote-btn", "is-down", votes.myVote === -1 && "is-active")}
        aria-pressed={votes.myVote === -1}
        aria-label={`Not helpful (${votes.downvotes})`}
        title={readOnly ? "You can't rate your own share" : "Not helpful"}
        disabled={readOnly}
        onClick={() => cast(-1)}
      >
        <ArrowBigDown size={icon} fill={votes.myVote === -1 ? "currentColor" : "none"} aria-hidden />
        <span>{votes.downvotes}</span>
      </button>
    </div>
  );
}
