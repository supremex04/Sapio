import { ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useLocation, useParams } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { formatRelative } from "../../shared/lib/format";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Card } from "../../shared/ui/Card";
import { PageTransition } from "../../shared/ui/PageTransition";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { sharingApi } from "./api";
import { ImportAction } from "./ExplorePage";
import { Comments } from "./Comments";
import { categoryEmoji, helpfulPercent, kindLabel, type ShareDetail, type Votes } from "./types";
import { VoteControl } from "./VoteControl";
import { useImport } from "./useImport";
import "./sharing.css";

/** One shared document: what it covers (its learning path) and the import action. */
export function SharedItemPage() {
  const { shareId = "" } = useParams();
  const [detail, setDetail] = useState<ShareDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const { importing, importShare } = useImport();
  const setVotes = (votes: Votes) => setDetail((d) => (d ? { ...d, share: { ...d.share, votes } } : d));
  const countComments = (delta: number) =>
    setDetail((d) => (d ? { ...d, share: { ...d.share, commentCount: d.share.commentCount + delta } } : d));

  const { hash } = useLocation();
  const loaded = detail !== null;
  useEffect(() => {
    // Card comment counts link to #comments: scroll there once the page is rendered.
    if (loaded && hash === "#comments") document.getElementById("comments")?.scrollIntoView({ behavior: "smooth" });
  }, [loaded, hash]);

  useEffect(() => {
    setDetail(null);
    sharingApi.detail(shareId).then(setDetail, (e) => setError(errorMessage(e)));
  }, [shareId]);

  return (
    <PageTransition>
      <div className="stack-lg shared-item">
        <Link to="/explore" className="small muted back-link">
          <ArrowLeft size={16} /> Explore
        </Link>
        {error ? (
          <Alert>{error}</Alert>
        ) : detail === null ? (
          <Card>
            <SkeletonText lines={6} />
          </Card>
        ) : (
          <>
            <Card className="shared-hero">
              <div className="row-between">
                <span className="share-chip">
                  <Emoji name={categoryEmoji(detail.share.category)} size={20} />
                  {detail.share.category.name}
                </span>
                <Badge>{kindLabel(detail.share.kind)}</Badge>
              </div>
              <h1 className="break">{detail.share.title}</h1>
              <p className="small muted">
                Shared by <strong>{detail.share.mine ? "you" : detail.share.sharedBy}</strong> · {formatRelative(detail.share.sharedAt)} ·{" "}
                {detail.share.pageCount} pages · imported {detail.share.importCount} {detail.share.importCount === 1 ? "time" : "times"}
              </p>
              {detail.description && <blockquote className="shared-note">{detail.description}</blockquote>}
              <p>{detail.share.summary}</p>
              <div className="shared-cta">
                <ImportAction share={detail.share} importing={importing === detail.share.id} onImport={() => importShare(detail.share)} size="lg" />
                {!detail.share.mine && !detail.share.importedDocumentId && (
                  <span className="small muted">
                    You get your own copy with its lessons and notes. What you study and generate stays yours; the original never changes.
                  </span>
                )}
              </div>
            </Card>

            <Card className="shared-rating">
              <div className="shared-rating-text">
                <h2 className="shared-section-title">Was it useful?</h2>
                <p className="small muted">
                  {helpfulPercent(detail.share.votes) === null
                    ? detail.share.mine
                      ? "No ratings yet."
                      : "No ratings yet. Rate it after you've studied with it."
                    : `${helpfulPercent(detail.share.votes)}% found it helpful · ${detail.share.votes.upvotes + detail.share.votes.downvotes} ratings`}
                </p>
              </div>
              <VoteControl shareId={detail.share.id} votes={detail.share.votes} readOnly={detail.share.mine} size="lg" onChange={setVotes} />
            </Card>

            <Card>
              <h2 className="shared-section-title">
                <Emoji name="compass" size={28} /> What you'll learn
              </h2>
              <ol className="shared-topics">
                {detail.topics.map((t, i) => (
                  <li key={i}>
                    <span className="shared-topic-num" aria-hidden>
                      {i + 1}
                    </span>
                    <span className="break">{t}</span>
                  </li>
                ))}
              </ol>
            </Card>

            <Comments shareId={detail.share.id} onCountChange={countComments} />
          </>
        )}
      </div>
    </PageTransition>
  );
}
