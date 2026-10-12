import { ArrowRight, Download, MessageSquare, Search } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { formatRelative } from "../../shared/lib/format";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button, ButtonLink } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { cx } from "../../shared/ui/cx";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { Segmented } from "../../shared/ui/Segmented";
import { Skeleton } from "../../shared/ui/Skeleton";
import { sharingApi } from "./api";
import { categoryEmoji, kindLabel, type Category, type ExploreSort, type ShareSummary, type Votes } from "./types";
import { VoteControl } from "./VoteControl";
import { useImport } from "./useImport";
import "./sharing.css";

const PAGE_SIZE = 24;

/** Courses other learners shared: browse by subject, search, import one into your library. */
export function ExplorePage() {
  const [categories, setCategories] = useState<Category[]>([]);
  const [category, setCategory] = useState<string | null>(null);
  const [sort, setSort] = useState<ExploreSort>("new");
  const [query, setQuery] = useState("");
  const [debounced, setDebounced] = useState("");
  const [items, setItems] = useState<ShareSummary[] | null>(null);
  const [page, setPage] = useState({ next: 0, total: 0 });
  const [loadingMore, setLoadingMore] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const request = useRef(0);
  const { importing, importShare } = useImport((shareId, documentId) =>
    setItems((list) => list?.map((s) => (s.id === shareId ? { ...s, importedDocumentId: documentId, importCount: s.importCount + 1 } : s)) ?? null),
  );

  useEffect(() => {
    sharingApi.categories().then(setCategories, (e) => setError(errorMessage(e)));
  }, []);

  useEffect(() => {
    const t = setTimeout(() => setDebounced(query), 300);
    return () => clearTimeout(t);
  }, [query]);

  const load = useCallback(
    async (pageNumber: number) => {
      const id = ++request.current;
      try {
        const result = await sharingApi.explore({ category, q: debounced, sort, page: pageNumber, size: PAGE_SIZE });
        if (id !== request.current) return; // a newer filter won
        setItems((list) => (pageNumber === 0 ? result.items : [...(list ?? []), ...result.items]));
        setPage({ next: pageNumber + 1, total: result.totalPages });
        setError(null);
      } catch (e) {
        if (id === request.current) setError(errorMessage(e));
      }
    },
    [category, debounced, sort],
  );

  useEffect(() => {
    setItems(null);
    void load(0);
  }, [load]);

  async function loadMore() {
    setLoadingMore(true);
    await load(page.next);
    setLoadingMore(false);
  }

  const filtered = category !== null || debounced.trim() !== "";

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="explore-header">
          <Emoji name="globe-showing-asia-australia" size={56} />
          <div className="lib-header-text">
            <h1>Explore</h1>
            <p className="muted">Courses other learners built from their documents. Import one and it's yours: lessons, practice and recall.</p>
          </div>
        </header>

        <div className="explore-toolbar">
          <label className="explore-search">
            <Search size={18} aria-hidden />
            <span className="visually-hidden">Search shared documents</span>
            <input className="input" type="search" placeholder="Search titles and summaries" value={query} onChange={(e) => setQuery(e.target.value)} />
          </label>
          <Segmented<ExploreSort>
            label="Sort"
            value={sort}
            onChange={setSort}
            options={[
              { value: "new", label: "Newest" },
              { value: "top", label: "Top rated" },
              { value: "popular", label: "Most imported" },
            ]}
          />
        </div>

        <div className="explore-categories" role="group" aria-label="Filter by category">
          <button type="button" className="chip" aria-pressed={category === null} onClick={() => setCategory(null)}>
            All
          </button>
          {categories.map((c) => (
            <button key={c.slug} type="button" className="chip" aria-pressed={category === c.slug} onClick={() => setCategory(category === c.slug ? null : c.slug)}>
              <Emoji name={categoryEmoji(c)} size={18} />
              {c.name}
            </button>
          ))}
        </div>

        {error && <Alert>{error}</Alert>}

        {items === null ? (
          <div className="share-grid">
            {[0, 1, 2, 3].map((i) => (
              <Skeleton key={i} height={240} radius="var(--radius-lg)" />
            ))}
          </div>
        ) : items.length === 0 ? (
          <Card>
            <EmptyState
              icon={<Emoji name={filtered ? "magnifying-glass-tilted-left" : "seedling"} size={64} />}
              title={filtered ? "Nothing matches yet" : "Nothing shared yet"}
              action={
                <ButtonLink to="/library" variant="soft">
                  Share from your library
                </ButtonLink>
              }
            >
              {filtered ? "Try another category or search." : "Be the first: share a document from your library and help others learn."}
            </EmptyState>
          </Card>
        ) : (
          <>
            <div className="share-grid">
              {items.map((s) => (
                <ShareCard
                  key={s.id}
                  share={s}
                  importing={importing === s.id}
                  onImport={() => importShare(s)}
                  onVotes={(votes) => setItems((list) => list?.map((x) => (x.id === s.id ? { ...x, votes } : x)) ?? null)}
                />
              ))}
            </div>
            {page.next < page.total && (
              <Button variant="secondary" className="explore-more" loading={loadingMore} onClick={loadMore}>
                Show more
              </Button>
            )}
          </>
        )}
      </div>
    </PageTransition>
  );
}

export function ShareCard({ share, importing, onImport, onVotes }: { share: ShareSummary; importing: boolean; onImport: () => void; onVotes: (votes: Votes) => void }) {
  return (
    <Card className="share-card">
      <div className="row-between share-card-top">
        <span className="share-chip">
          <Emoji name={categoryEmoji(share.category)} size={18} />
          {share.category.name}
        </span>
        <Badge>{kindLabel(share.kind)}</Badge>
      </div>
      <Link to={`/explore/${share.id}`} className="share-title break">
        {share.title}
      </Link>
      <p className="small share-summary">{share.summary}</p>
      <p className="xs muted">
        {share.topicCount} topic{share.topicCount === 1 ? "" : "s"} · {share.pageCount} page{share.pageCount === 1 ? "" : "s"}
      </p>
      <div className="share-card-foot">
        <span className="xs muted break">
          by <strong>{share.mine ? "you" : share.sharedBy}</strong> · {formatRelative(share.sharedAt)}
        </span>
        <span className={cx("xs share-imports", share.importCount > 0 && "is-popular")} title="Times imported">
          <Download size={14} aria-hidden /> {share.importCount}
          <span className="visually-hidden"> imports</span>
        </span>
      </div>
      <div className="share-actions">
        <ImportAction share={share} importing={importing} onImport={onImport} />
        <span className="share-feedback">
          <VoteControl shareId={share.id} votes={share.votes} readOnly={share.mine} onChange={onVotes} />
          <Link to={`/explore/${share.id}#comments`} className="share-comments" aria-label={`${share.commentCount} comments`}>
            <MessageSquare size={16} aria-hidden /> {share.commentCount}
          </Link>
        </span>
      </div>
    </Card>
  );
}

export function ImportAction({ share, importing, onImport, size = "sm" }: { share: ShareSummary; importing: boolean; onImport: () => void; size?: "sm" | "md" | "lg" }) {
  if (share.mine) {
    return <Badge tone="review">Shared by you</Badge>;
  }
  if (share.importedDocumentId) {
    return (
      <ButtonLink to={`/documents/${share.importedDocumentId}`} variant="soft" size={size} iconRight={<ArrowRight size={16} />}>
        Open your copy
      </ButtonLink>
    );
  }
  return (
    <Button variant="primary" size={size} icon={<Download size={16} />} loading={importing} onClick={onImport}>
      Import
    </Button>
  );
}
