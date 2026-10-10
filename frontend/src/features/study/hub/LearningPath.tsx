import { ArrowRight, Check } from "lucide-react";
import { Link } from "react-router";
import { ButtonLink } from "../../../shared/ui/Button";
import { cx } from "../../../shared/ui/cx";
import { masteryLabel, nodeState, startLabel, type NodeState } from "../path";
import type { Topic } from "../types";

const STATE_LABEL: Record<NodeState, string> = {
  completed: "Done",
  recommended: "Up next",
  "in-progress": "In progress",
  upcoming: "Not started",
};

/**
 * The learning path on the document page: a one-line overview strip (one segment per topic) and a vertical
 * timeline whose rail fills as topics are completed. Progress you can see at a glance motivates (goal-gradient)
 * without a separate full-screen map; "Up next" is the one obvious action.
 */
export function LearningPath({ documentId, topics }: { documentId: string; topics: Topic[] }) {
  const done = topics.filter((t) => t.status === "COMPLETED").length;
  return (
    <div className="lpath">
      <PathStrip documentId={documentId} topics={topics} done={done} />
      <ol className="lpath-list">
        {topics.map((t, i) => {
          const state = nodeState(t);
          const mastery = t.mastery;
          // The rail below a node is filled when that topic is done (the path "lights up" as you go).
          const railDone = t.status === "COMPLETED";
          return (
            <li key={t.id} className={cx("lpath-item", `is-${state}`, i === topics.length - 1 && "is-last", railDone && "rail-done")}>
              <span className="lpath-node" aria-hidden>
                {t.status === "COMPLETED" ? <Check size={16} strokeWidth={3.5} /> : t.position}
              </span>
              <div className="lpath-body">
                <Link to={`/documents/${documentId}/learn/${t.id}`} className="lpath-title">
                  {t.title}
                </Link>
                <span className="visually-hidden">, {STATE_LABEL[state]}</span>
                <p className="small muted lpath-summary">{t.summary}</p>
                <div className="lpath-meta">
                  {state === "recommended" ? (
                    <ButtonLink to={`/documents/${documentId}/learn/${t.id}`} variant="primary" size="sm" iconRight={<ArrowRight size={16} />}>
                      {startLabel(t)}
                    </ButtonLink>
                  ) : (
                    <span className={cx("lpath-state xs", `is-${state}`)}>{STATE_LABEL[state]}</span>
                  )}
                  {mastery !== null ? (
                    <span className="lpath-mastery xs" title="Mastery from your checks, tests and reviews (recent answers count most)">
                      <span className="lpath-mastery-bar" aria-hidden>
                        <span style={{ width: `${Math.round(mastery * 100)}%` }} />
                      </span>
                      {Math.round(mastery * 100)}% · {masteryLabel(mastery)}
                    </span>
                  ) : (
                    <span className="xs muted">Not practised yet</span>
                  )}
                </div>
              </div>
            </li>
          );
        })}
      </ol>
    </div>
  );
}

/** One segment per topic, coloured by state: the whole course at a glance. */
function PathStrip({ documentId, topics, done }: { documentId: string; topics: Topic[]; done: number }) {
  return (
    <div className="lpath-strip">
      <div className="row-between">
        <strong className="small">
          {done} of {topics.length} topics done
        </strong>
        <span className="xs muted">{done === topics.length ? "Course complete" : "Every topic you finish lights up the path"}</span>
      </div>
      <div className="lpath-segments" role="list" aria-label="Course progress by topic">
        {topics.map((t) => {
          const state = nodeState(t);
          return (
            <Link
              key={t.id}
              role="listitem"
              to={`/documents/${documentId}/learn/${t.id}`}
              className={cx("lpath-segment", `is-${state}`)}
              title={`${t.position}. ${t.title} · ${STATE_LABEL[state]}`}
              aria-label={`${t.position}. ${t.title}: ${STATE_LABEL[state]}`}
            />
          );
        })}
      </div>
    </div>
  );
}
