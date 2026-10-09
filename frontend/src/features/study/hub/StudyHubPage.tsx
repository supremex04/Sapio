import { ArrowLeft, ArrowRight, BookOpen, Brain, CheckCircle2, ClipboardList, FileText, Layers, ListTree, RotateCcw } from "lucide-react";
import { motion } from "motion/react";
import { useCallback, useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { usePolling } from "../../../shared/hooks/usePolling";
import { formatPercent, formatRelative } from "../../../shared/lib/format";
import { Alert } from "../../../shared/ui/Alert";
import { Badge } from "../../../shared/ui/Badge";
import { Button, ButtonLink } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { EmptyState } from "../../../shared/ui/EmptyState";
import { Markdown } from "../../../shared/ui/Markdown";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { ProgressBar } from "../../../shared/ui/Progress";
import { Skeleton, SkeletonText } from "../../../shared/ui/Skeleton";
import { Tabs } from "../../../shared/ui/Tabs";
import { documentsApi } from "../../documents/api";
import { ExtractedText } from "../../documents/ExtractedText";
import { pipelineState } from "../../documents/pipeline";
import { PipelineStepper } from "../../documents/PipelineStepper";
import { displayTitle, hasExtractedText, isTerminal, type DocumentDto } from "../../documents/types";
import "../../documents/documents.css";
import { studyApi } from "../api";
import { MasteryMeter } from "../components/MasteryMeter";
import { reviewMinutes } from "../nextStep";
import { recommendedTopic } from "../path";
import type { AssessmentSummary, Material } from "../types";
import "../study.css";

type Tab = "topics" | "tests" | "text";

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
  usePolling(load, 2500, doc !== null && !isTerminal(doc.status));

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
        <div>
          <BackToLibrary />
          <p className="eyebrow">Study guide</p>
          <h1 className="hub-title break">{material?.title ?? displayTitle(doc)}</h1>
          {material?.summary && (
            <div className="hub-summary">
              <Markdown>{material.summary}</Markdown>
            </div>
          )}
          <div className="row" style={{ marginTop: "var(--space-3)" }}>
            {material && <Badge icon={<ListTree />}>{material.stats.topicCount} topics</Badge>}
            {doc.pageCount != null && <Badge icon={<FileText />}>{doc.pageCount} pages</Badge>}
            {material?.language && <Badge>{material.language.toUpperCase()}</Badge>}
          </div>
        </div>

        {!ready && (
          <Card className="stack">
            <PipelineStepper doc={doc} />
            {state.failed ? (
              <Alert action={<Button size="sm" variant="soft" icon={<RotateCcw size={14} />} onClick={retry}>Try again</Button>}>
                {doc.errorMessage ?? "Something interrupted processing."} Your file is safe; trying again usually works.
              </Alert>
            ) : (
              <p className="muted small center">
                {doc.status === "ANALYZING"
                  ? "Reading done. Ved is organising the material into topics. This usually takes under a minute."
                  : "Reading your document. Scanned pages take a little longer."}
              </p>
            )}
          </Card>
        )}

        {ready && material && <ModeCards documentId={id} material={material} />}

        {(ready || hasExtractedText(doc.status)) && (
          <section>
            <Tabs
              label="Study guide sections"
              value={ready ? tab : "text"}
              onChange={setTab}
              items={[
                ...(ready
                  ? [
                      { value: "topics" as const, label: <><ListTree size={16} aria-hidden /> Topics</> },
                      { value: "tests" as const, label: <><ClipboardList size={16} aria-hidden /> Tests{assessments?.length ? ` (${assessments.length})` : ""}</> },
                    ]
                  : []),
                { value: "text" as const, label: <><FileText size={16} aria-hidden /> Extracted text</> },
              ]}
            />
            <div style={{ paddingTop: "var(--space-6)" }}>
              {ready && tab === "topics" && material && <TopicList documentId={id} material={material} />}
              {ready && tab === "tests" && <AssessmentList documentId={id} items={assessments} />}
              {(!ready || tab === "text") && <ExtractedText doc={doc} />}
            </div>
          </section>
        )}
      </div>
    </PageTransition>
  );
}

function BackToLibrary() {
  return (
    <Link to="/library" className="back-link">
      <ArrowLeft size={16} aria-hidden /> Library
    </Link>
  );
}

function HubSkeleton() {
  return (
    <div className="stack-lg">
      <Skeleton width="40%" height={36} />
      <SkeletonText lines={2} />
      <div className="mode-grid">
        <Skeleton height={200} radius="var(--radius-xl)" />
        <Skeleton height={200} radius="var(--radius-xl)" />
      </div>
    </div>
  );
}

/** The two primary choices (autonomy), each with a clear time estimate. */
function ModeCards({ documentId, material }: { documentId: string; material: Material }) {
  const next = recommendedTopic(material.topics);
  const { completedTopics, topicCount, reviewsDue, bestScore } = material.stats;
  const started = material.topics.some((t) => t.status !== "NOT_STARTED");
  return (
    <div className="stack">
      <div className="mode-grid">
        <motion.div whileHover={{ y: -3 }} transition={{ duration: 0.2 }}>
          <Link to={next ? `/documents/${documentId}/learn/${next.id}` : `/documents/${documentId}/learn`} className="mode-card mode-learn">
            <div className="mode-icon" aria-hidden>
              <BookOpen size={26} />
            </div>
            <div className="stack-sm">
              <h2>Learn</h2>
              <p className="text-2">A tutor walks you through each topic with examples and quick checks.</p>
            </div>
            <div className="mode-progress">
              <div className="row-between xs">
                <span className="muted">
                  {completedTopics}/{topicCount} topics
                </span>
                {next && <span className="muted">~6 min per topic</span>}
              </div>
              <ProgressBar value={topicCount ? completedTopics / topicCount : 0} label="Topics completed" size="sm" />
            </div>
            <span className="mode-cta">
              {next ? `${started ? "Continue" : "Start"}: ${next.title}` : "Revisit any topic"} <ArrowRight size={16} aria-hidden />
            </span>
          </Link>
        </motion.div>
        <motion.div whileHover={{ y: -3 }} transition={{ duration: 0.2 }}>
          <Link to={`/documents/${documentId}/test`} className="mode-card mode-test">
            <div className="mode-icon" aria-hidden>
              <Brain size={26} />
            </div>
            <div className="stack-sm">
              <h2>Test my knowledge</h2>
              <p className="text-2">Questions from your material with instant feedback and a report on what to strengthen.</p>
            </div>
            <div className="mode-progress">
              <div className="row-between xs">
                <span className="muted">{bestScore != null ? `Best score ${formatPercent(bestScore)}` : "No tests yet"}</span>
                <span className="muted">~5–10 min</span>
              </div>
            </div>
            <span className="mode-cta">
              Set up a test <ArrowRight size={16} aria-hidden />
            </span>
          </Link>
        </motion.div>
      </div>
      {reviewsDue > 0 && (
        <Link to={`/review?documentId=${documentId}`} className="review-strip">
          <Layers size={18} aria-hidden />
          <span>
            <strong>{reviewsDue} review{reviewsDue === 1 ? "" : "s"} due</strong> · ~{reviewMinutes(reviewsDue)} min. A quick recall now keeps it from fading.
          </span>
          <ArrowRight size={16} aria-hidden style={{ marginLeft: "auto" }} />
        </Link>
      )}
    </div>
  );
}

function TopicList({ documentId, material }: { documentId: string; material: Material }) {
  return (
    <ol className="topic-list">
      {material.topics.map((t) => (
        <li key={t.id}>
          <Link to={`/documents/${documentId}/learn/${t.id}`} className="topic-row">
            <span className={`topic-num ${t.status === "COMPLETED" ? "done" : ""}`} aria-hidden>
              {t.status === "COMPLETED" ? <CheckCircle2 size={18} /> : t.position}
            </span>
            <span className="topic-main">
              <span className="row">
                <strong>{t.title}</strong>
                {t.recommended && <Badge tone="primary">Up next</Badge>}
                {t.status === "IN_PROGRESS" && !t.recommended && <Badge>In progress</Badge>}
              </span>
              <span className="small muted">{t.summary}</span>
            </span>
            <MasteryMeter mastery={t.mastery} />
          </Link>
        </li>
      ))}
    </ol>
  );
}

function AssessmentList({ documentId, items }: { documentId: string; items: AssessmentSummary[] | null }) {
  if (!items) return <SkeletonText lines={3} />;
  if (items.length === 0) {
    return (
      <EmptyState
        icon={<Brain size={24} />}
        title="No tests yet"
        action={
          <ButtonLink to={`/documents/${documentId}/test`} variant="primary">
            Test my knowledge
          </ButtonLink>
        }
      >
        Testing yourself is one of the most effective ways to learn, even before you feel ready.
      </EmptyState>
    );
  }
  return (
    <ul className="assessment-list">
      {items.map((a) => (
        <li key={a.id}>
          <Link to={`/assessments/${a.id}`} className="assessment-row">
            <span className="assessment-score" data-level={a.score == null ? "none" : a.score >= 0.8 ? "high" : a.score >= 0.5 ? "mid" : "low"}>
              {a.score == null ? "…" : formatPercent(a.score)}
            </span>
            <span className="topic-main">
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
