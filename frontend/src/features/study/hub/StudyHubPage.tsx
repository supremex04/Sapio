import { ArrowLeft, ArrowRight, RotateCcw } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { usePolling } from "../../../shared/hooks/usePolling";
import { Emoji } from "../../../shared/illustrations/Emoji";
import { formatPercent, formatRelative } from "../../../shared/lib/format";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { Button, ButtonLink } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { Chip } from "../../../shared/ui/Chip";
import { cx } from "../../../shared/ui/cx";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { Markdown } from "../../../shared/ui/Markdown";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { ProgressBar } from "../../../shared/ui/Progress";
import { Skeleton, SkeletonText } from "../../../shared/ui/Skeleton";
import { Tabs } from "../../../shared/ui/Tabs";
import { documentsApi } from "../../documents/api";
import { OriginTag } from "../../sharing/OriginTag";
import { DocumentPreview } from "../../documents/DocumentPreview";
import { LearningPath } from "./LearningPath";
import { pipelineState } from "../../documents/pipeline";
import { PipelineStepper } from "../../documents/PipelineStepper";
import { DocumentWorkbench } from "../../documents/DocumentWorkbench";
import { displayTitle, hasExtractedText, isSettled, type DocumentDto, isMedia, lengthLabel } from "../../documents/types";
import "../../documents/documents.css";
import { studyApi } from "../api";
import { reviewMinutes } from "../nextStep";
import { recommendedTopic } from "../path";
import type { AssessmentSummary, Material } from "../types";
import "./hub.css";

type Tab = "topics" | "preview" | "tests";

/** Rough lesson length used for time estimates ("~6 min per topic"). */
const MINUTES_PER_TOPIC = 6;

export function StudyHubPage() {
  const { id = "" } = useParams();
  const [doc, setDoc] = useState<DocumentDto | null>(null);
  const [material, setMaterial] = useState<Material | null>(null);
  const [assessments, setAssessments] = useState<AssessmentSummary[] | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [tab, setTab] = useState<Tab>("topics");

  const load = useCallback(async () => {
    try {
      const d = await documentsApi.get(id);
      setDoc(d);
      if (d.status === "READY") {
        const [m, a] = await Promise.all([studyApi.material(id), studyApi.assessments(id)]);
        setMaterial(m);
        setAssessments(a);
      }
    } catch (e) {
      setError(errorMessage(e));
    }
  }, [id]);

  useEffect(() => {
    void load();
  }, [load]);
  usePolling(load, 2500, doc !== null && !isSettled(doc));

  async function retry() {
    try {
      setDoc(await documentsApi.retry(id));
      setError(null);
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  if (error && !doc) {
    return (
      <div className="stack">
        <BackToLibrary />
        <Alert>{error}</Alert>
      </div>
    );
  }
  if (!doc) return <HubSkeleton />;

  const ready = doc.status === "READY";
  const state = pipelineState(doc);

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="hub-header">
          <BackToLibrary />
          <div className="hub-title-row">
            <span className="hub-art" aria-hidden>
              <Emoji name="document" size={44} />
            </span>
            <div className="hub-title-text">
              <p className="eyebrow">Your course</p>
              <h1 className="break">{material?.title ?? displayTitle(doc)}</h1>
            </div>
          </div>
          {material?.summary && (
            <div className="hub-summary">
              <Markdown>{material.summary}</Markdown>
            </div>
          )}
          <div className="row">
            {material && <Chip emoji="card-index-dividers">{material.stats.topicCount} topics</Chip>}
            {lengthLabel(doc) && <Chip emoji={isMedia(doc) ? "recall" : "document"}>{lengthLabel(doc)}</Chip>}
            {material?.language && <Chip>{material.language.toUpperCase()}</Chip>}
            {doc.origin && <OriginTag origin={doc.origin} />}
          </div>
        </header>

        {!ready && (
          <Card className="hub-processing">
            {state.failed ? (
              <div className="stack">
                <PipelineStepper doc={doc} />
                <Alert
                  action={
                    <Button size="sm" variant="danger" icon={<RotateCcw size={16} />} onClick={retry}>
                      Retry
                    </Button>
                  }
                >
                  {doc.errorMessage ?? "Something interrupted processing."} Your file is safe; trying again usually works.
                </Alert>
              </div>
            ) : (
              <div className="stack">
                <PipelineStepper doc={doc} />
                <DocumentWorkbench doc={doc} />
                <p className="muted small center">Most documents are ready in a minute or two. Feel free to look around meanwhile.</p>
              </div>
            )}
          </Card>
        )}

        {ready && material && <ModeCards documentId={id} material={material} />}

        {(ready || hasExtractedText(doc.status)) && (
          <section>
            <Tabs
              label="Course sections"
              value={ready ? tab : "preview"}
              onChange={setTab}
              items={[
                ...(ready ? [{ value: "topics" as const, label: "Topics" }] : []),
                { value: "preview" as const, label: "Preview" },
                ...(ready ? [{ value: "tests" as const, label: `Tests${assessments?.length ? ` (${assessments.length})` : ""}` }] : []),
              ]}
            />
            <div className="hub-panel">
              {ready && tab === "topics" && material && <LearningPath documentId={id} topics={material.topics} />}
              {ready && tab === "tests" && <AssessmentList documentId={id} items={assessments} />}
              {(!ready || tab === "preview") && (
                <Card>
                  <DocumentPreview doc={doc} ready={ready} />
                </Card>
              )}
            </div>
          </section>
        )}
      </div>
    </PageTransition>
  );
}

function BackToLibrary() {
  return (
    <Link to="/library" className="hub-back">
      <ArrowLeft size={18} strokeWidth={2.5} aria-hidden /> Library
    </Link>
  );
}

function HubSkeleton() {
  return (
    <div className="stack-lg">
      <Skeleton width="50%" height={36} />
      <SkeletonText lines={2} />
      <div className="hub-modes">
        <Skeleton height={240} radius="var(--radius-xl)" />
        <Skeleton height={240} radius="var(--radius-xl)" />
      </div>
    </div>
  );
}

/** The two primary choices (autonomy), each with a clear time estimate. */
function ModeCards({ documentId, material }: { documentId: string; material: Material }) {
  const next = recommendedTopic(material.topics);
  const { completedTopics, topicCount, reviewsDue, bestScore, assessmentsCompleted } = material.stats;
  const started = material.topics.some((t) => t.status !== "NOT_STARTED");
  const minutesLeft = Math.max(1, topicCount - completedTopics) * MINUTES_PER_TOPIC;
  return (
    <div className="stack">
      <div className="hub-modes">
        <Link to={next ? `/documents/${documentId}/learn/${next.id}` : `/documents/${documentId}`} className="mode-card mode-card-learn">
          <span className="mode-art" aria-hidden>
            <Emoji name="open-book" size={56} />
          </span>
          <div className="mode-text">
            <h2>Learn</h2>
            <p className="text-2">Your tutor explains each topic with examples and quick checks.</p>
          </div>
          <div className="mode-meta">
            <div className="row-between small">
              <strong>
                {completedTopics}/{topicCount} topics
              </strong>
              <span className="muted">~{minutesLeft} min left</span>
            </div>
            <ProgressBar value={topicCount ? completedTopics / topicCount : 0} label="Topics completed" />
          </div>
          <span className="btn btn-primary btn-block mode-btn">
            {next ? (started ? "Continue" : "Start learning") : "Revisit topics"} <ArrowRight size={18} aria-hidden />
          </span>
        </Link>

        <Link to={`/documents/${documentId}/test`} className="mode-card mode-card-test">
          <span className="mode-art" aria-hidden>
            <Emoji name={bestScore != null && bestScore >= 0.8 ? "trophy" : "bullseye"} size={56} />
          </span>
          <div className="mode-text">
            <h2>Test my knowledge</h2>
            <p className="text-2">Questions from your material, then a report with every answer explained and what to strengthen.</p>
          </div>
          <div className="mode-meta">
            <div className="row-between small">
              <strong>{bestScore != null ? `Best ${formatPercent(bestScore)}` : "No tests yet"}</strong>
              <span className="muted">~5–10 min</span>
            </div>
            {assessmentsCompleted > 0 && <span className="small muted">{assessmentsCompleted} completed</span>}
          </div>
          <span className="btn btn-mastery btn-block mode-btn">
            Set up a test <ArrowRight size={18} aria-hidden />
          </span>
        </Link>
      </div>

      {reviewsDue > 0 && (
        <Link to={`/recall?documentId=${documentId}`} className="card card-tight card-interactive card-teal hub-review">
          <Emoji name="recall" size={36} />
          <span className="hub-review-text">
            <strong>
              {reviewsDue} card{reviewsDue === 1 ? "" : "s"} to recall · ~{reviewMinutes(reviewsDue)} min
            </strong>
            <span className="small">A quick recall now keeps it from fading.</span>
          </span>
          <ArrowRight size={20} aria-hidden />
        </Link>
      )}
    </div>
  );
}

function scoreTone(score: number): "success" | "warning" | "danger" {
  return score >= 0.8 ? "success" : score >= 0.5 ? "warning" : "danger";
}

function AssessmentList({ documentId, items }: { documentId: string; items: AssessmentSummary[] | null }) {
  if (!items) return <SkeletonText lines={3} />;
  if (items.length === 0) {
    return (
      <Card>
        <EmptyState
          mood="encourage"
          title="No tests yet"
          action={
            <ButtonLink to={`/documents/${documentId}/test`} variant="mastery">
              Test my knowledge
            </ButtonLink>
          }
        >
          Testing yourself is one of the most effective ways to learn, even before you feel ready.
        </EmptyState>
      </Card>
    );
  }
  return (
    <ul className="hub-list">
      {items.map((a) => (
        <li key={a.id}>
          <Link to={`/assessments/${a.id}`} className="hub-row">
            <span className={cx("hub-score", a.score != null && `is-${scoreTone(a.score)}`)}>{a.score == null ? "…" : formatPercent(a.score)}</span>
            <span className="hub-row-main">
              <strong>
                {a.questionCount} questions · {a.difficulty.toLowerCase()}
              </strong>
              <span className="small muted">
                {a.status === "COMPLETED" ? `Finished ${formatRelative(a.completedAt ?? a.createdAt)}` : `In progress · ${a.answeredCount}/${a.questionCount} answered`}
              </span>
            </span>
            {a.status === "IN_PROGRESS" ? <Badge tone="primary">Resume</Badge> : <Badge>Report</Badge>}
          </Link>
        </li>
      ))}
    </ul>
  );
}
