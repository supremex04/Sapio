import { ArrowLeft, BookOpen, Check } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useState, type CSSProperties } from "react";
import { Link, useParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { Dia } from "../../../shared/illustrations/Dia";
import { Emoji } from "../../../shared/illustrations/Emoji";
import { Alert } from "../../../shared/ui/Alert";
import { ButtonLink } from "../../../shared/ui/Button";
import { cx } from "../../../shared/ui/cx";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { ProgressBar } from "../../../shared/ui/Progress";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { studyApi } from "../api";
import { masteryLabel, nodeState, pathWave, startLabel, type NodeState } from "../path";
import type { Material, Topic } from "../types";
import "./learn.css";

const NODE_SIZE = 76;
const RING_SIZE = NODE_SIZE + 18;

/**
 * The learning path: a winding column of round 3D nodes, one per topic, ending in a test.
 * Visual progress (goal gradient, endowed progress) with one clear recommended next step.
 */
export function LearnPathPage() {
  const { id = "" } = useParams();
  const [material, setMaterial] = useState<Material | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    studyApi.material(id).then(setMaterial, (e) => setError(errorMessage(e)));
  }, [id]);

  if (error) return <Alert>{error}</Alert>;
  if (!material) {
    return (
      <div className="stack">
        <Skeleton height={150} radius="var(--radius-xl)" />
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} width={NODE_SIZE} height={NODE_SIZE} radius="50%" style={{ margin: "0 auto" }} />
        ))}
      </div>
    );
  }

  const { completedTopics, topicCount } = material.stats;
  const topics = [...material.topics].sort((a, b) => a.position - b.position);
  return (
    <PageTransition>
      <div className="lp">
        <Link to={`/documents/${id}`} className="lp-back">
          <ArrowLeft size={18} strokeWidth={2.5} aria-hidden /> Study guide
        </Link>

        <section className="lp-banner" aria-label="Unit">
          <div className="lp-banner-text">
            <p className="lp-eyebrow">
              Unit · {topicCount} topic{topicCount === 1 ? "" : "s"}
            </p>
            <h1 className="break">{material.title}</h1>
            <div className="lp-banner-progress">
              <ProgressBar value={topicCount ? completedTopics / topicCount : 0} label="Topics completed" color="var(--amber-main)" />
              <span className="nowrap">
                {completedTopics}/{topicCount} done
              </span>
            </div>
          </div>
          <ButtonLink to={`/documents/${id}/test`} className="lp-banner-btn">
            <Emoji name="trophy" size={22} /> Test yourself
          </ButtonLink>
        </section>

        <ol className="lp-path">
          {topics.map((topic, i) => (
            <PathNode key={topic.id} topic={topic} index={i} documentId={id} />
          ))}
          <li className="lp-node">
            <div className="lp-shift" style={{ "--wave": pathWave(topics.length) } as CSSProperties}>
              <div className="lp-slot">
                <Link to={`/documents/${id}/test`} className="lp-btn lp-btn-trophy" aria-label="Test yourself on the whole unit">
                  <Emoji name="trophy" size={42} />
                </Link>
              </div>
              <span className="lp-title">Test yourself</span>
              <span className="lp-meta">All topics, mixed</span>
            </div>
          </li>
        </ol>
      </div>
    </PageTransition>
  );
}

function PathNode({ topic, index, documentId }: { topic: Topic; index: number; documentId: string }) {
  const state = nodeState(topic);
  const wave = pathWave(index);
  const mastered = topic.mastery != null && topic.mastery >= 0.8;
  return (
    <motion.li
      className={cx("lp-node", `lp-${state}`)}
      initial={{ opacity: 0, scale: 0.85 }}
      animate={{ opacity: 1, scale: 1 }}
      transition={{ delay: index * 0.05, type: "spring", stiffness: 380, damping: 24 }}
    >
      <div className="lp-shift" style={{ "--wave": wave } as CSSProperties}>
        <div className="lp-slot">
          {state === "recommended" && (
            <>
              <span className="lp-start" aria-hidden>
                {startLabel(topic)}
              </span>
              <div className={cx("lp-dia", wave > 0 ? "lp-dia-left" : "lp-dia-right")}>
                <Dia mood="happy" size={84} />
              </div>
            </>
          )}
          {topic.mastery != null && <MasteryRing mastery={topic.mastery} />}
          <Link
            to={`/documents/${documentId}/learn/${topic.id}`}
            className={cx("lp-btn", `lp-btn-${state}`)}
            aria-label={`Topic ${topic.position}: ${topic.title}. ${stateLabel(state)}. ${masteryLabel(topic.mastery)}.`}
          >
            <NodeIcon state={state} mastered={mastered} />
          </Link>
        </div>
        <span className="lp-title">{topic.title}</span>
        <span className="lp-meta">{topic.difficulty === "INTRO" ? "Intro" : topic.difficulty === "CORE" ? "Core" : "Advanced"} · ~6 min</span>
      </div>
    </motion.li>
  );
}

function NodeIcon({ state, mastered }: { state: NodeState; mastered: boolean }) {
  if (state === "completed") return mastered ? <Emoji name="crown" size={38} /> : <Check size={36} strokeWidth={4} aria-hidden />;
  if (state === "recommended") return <Emoji name="glowing-star" size={40} />;
  if (state === "in-progress") return <BookOpen size={32} strokeWidth={2.75} aria-hidden />;
  return <Emoji name="open-book" size={36} />;
}

function stateLabel(state: NodeState): string {
  return { completed: "Completed", recommended: "Recommended next", "in-progress": "In progress", upcoming: "Not started" }[state];
}

/** Purple ring around a node showing mastery (0..1). */
function MasteryRing({ mastery }: { mastery: number }) {
  const r = (RING_SIZE - 7) / 2;
  const c = 2 * Math.PI * r;
  return (
    <svg className="lp-ring" width={RING_SIZE} height={RING_SIZE} viewBox={`0 0 ${RING_SIZE} ${RING_SIZE}`} aria-hidden>
      <circle cx={RING_SIZE / 2} cy={RING_SIZE / 2} r={r} fill="none" strokeWidth={6} className="lp-ring-track" />
      <circle
        cx={RING_SIZE / 2}
        cy={RING_SIZE / 2}
        r={r}
        fill="none"
        strokeWidth={6}
        strokeLinecap="round"
        strokeDasharray={c}
        strokeDashoffset={c * (1 - Math.min(1, Math.max(0, mastery)))}
        className="lp-ring-fill"
      />
    </svg>
  );
}
