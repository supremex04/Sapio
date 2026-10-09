import { ArrowLeft, Check, Play, Star } from "lucide-react";
import { motion } from "motion/react";
import { useEffect, useState } from "react";
import { Link, useParams } from "react-router";
import { errorMessage } from "../../../shared/api/errors";
import { Alert } from "../../../shared/ui/Alert";
import { cx } from "../../../shared/ui/cx";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { ProgressBar } from "../../../shared/ui/Progress";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { studyApi } from "../api";
import { MasteryMeter } from "../components/MasteryMeter";
import { nodeState } from "../path";
import type { Material } from "../types";
import "../study.css";

/** The topic path: a calm, visual map of progress (goal-gradient, endowed progress). */
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
        <Skeleton width="50%" height={36} />
        {[0, 1, 2, 3].map((i) => (
          <Skeleton key={i} height={84} radius="var(--radius-lg)" />
        ))}
      </div>
    );
  }

  const { completedTopics, topicCount } = material.stats;
  return (
    <PageTransition>
      <div className="path-page">
        <Link to={`/documents/${id}`} className="back-link">
          <ArrowLeft size={16} aria-hidden /> Study guide
        </Link>
        <header className="path-header">
          <p className="eyebrow">Learning path</p>
          <h1 className="break">{material.title}</h1>
          <div className="path-progress">
            <ProgressBar value={topicCount ? completedTopics / topicCount : 0} label="Topics completed" />
            <span className="small muted nowrap">
              {completedTopics} of {topicCount} topics
            </span>
          </div>
        </header>

        <ol className="path">
          {material.topics.map((topic, i) => {
            const state = nodeState(topic);
            return (
              <motion.li
                key={topic.id}
                className={cx("path-node", `path-${state}`, i % 2 === 1 && "path-alt")}
                initial={{ opacity: 0, y: 12 }}
                animate={{ opacity: 1, y: 0 }}
                transition={{ delay: i * 0.05, duration: 0.35, ease: [0.22, 1, 0.36, 1] }}
              >
                <Link to={`/documents/${id}/learn/${topic.id}`} className="path-link" aria-label={`Topic ${topic.position}: ${topic.title} (${state.replace("-", " ")})`}>
                  <span className="path-circle" aria-hidden>
                    {state === "completed" ? <Check size={22} strokeWidth={3} /> : state === "recommended" ? <Play size={20} fill="currentColor" /> : <span>{topic.position}</span>}
                    {state === "recommended" && <span className="path-ring" />}
                  </span>
                  <span className="path-card">
                    {state === "recommended" && (
                      <span className="path-flag">
                        <Star size={12} fill="currentColor" aria-hidden /> {topic.status === "COMPLETED" ? "Strengthen this" : topic.status === "IN_PROGRESS" ? "Pick up here" : "Start here"}
                      </span>
                    )}
                    <strong>{topic.title}</strong>
                    <span className="small muted">{topic.summary}</span>
                    <span className="row-between" style={{ marginTop: 6 }}>
                      <span className="xs muted">{topic.difficulty === "INTRO" ? "Intro" : topic.difficulty === "CORE" ? "Core" : "Advanced"} · ~6 min</span>
                      <MasteryMeter mastery={topic.mastery} showLabel={false} />
                    </span>
                  </span>
                </Link>
              </motion.li>
            );
          })}
        </ol>
      </div>
    </PageTransition>
  );
}
