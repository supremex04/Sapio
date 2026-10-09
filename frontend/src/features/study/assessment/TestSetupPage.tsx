import { ArrowLeft, Brain, Clock, Shuffle } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { ApiError, errorMessage } from "../../../shared/api/errors";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { Card } from "../../../shared/ui/Card";
import { cx } from "../../../shared/ui/cx";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { Segmented } from "../../../shared/ui/Segmented";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { studyApi } from "../api";
import { GeneratingState } from "../components/GeneratingState";
import type { Difficulty, Material } from "../types";
import "../study.css";

const COUNTS = [5, 10, 15, 20] as const;

export function TestSetupPage() {
  const { id = "" } = useParams();
  const navigate = useNavigate();
  const [material, setMaterial] = useState<Material | null>(null);
  const [count, setCount] = useState<number>(10);
  const [difficulty, setDifficulty] = useState<Difficulty>("MIXED");
  const [selected, setSelected] = useState<Set<string>>(new Set());
  const [creating, setCreating] = useState(false);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    studyApi.material(id).then(setMaterial, (e) => setError(errorMessage(e)));
  }, [id]);

  function toggle(topicId: string) {
    setSelected((s) => {
      const next = new Set(s);
      if (next.has(topicId)) next.delete(topicId);
      else next.add(topicId);
      return next;
    });
  }

  async function start() {
    setCreating(true);
    setError(null);
    try {
      const a = await studyApi.createAssessment(id, { questionCount: count, difficulty, topicIds: selected.size ? [...selected] : undefined });
      navigate(`/assessments/${a.id}`, { replace: true });
    } catch (e) {
      setError(e instanceof ApiError && e.code === "AI_UNAVAILABLE" ? "The question writer is busy right now. Please try again in a moment." : errorMessage(e));
      setCreating(false);
    }
  }

  if (creating) {
    return <GeneratingState title="Writing your questions" subtitle={`${count} questions from your material, mixed across topics so you practise telling ideas apart.`} />;
  }

  const weak = material?.topics.filter((t) => t.mastery != null && t.mastery < 0.5) ?? [];

  return (
    <PageTransition>
      <div className="setup">
        <Link to={`/documents/${id}`} className="back-link">
          <ArrowLeft size={16} aria-hidden /> Study guide
        </Link>
        <header className="page-header">
          <div>
            <p className="eyebrow">Test my knowledge</p>
            <h1>Set up your test</h1>
            <p>Recalling answers strengthens memory far more than re-reading, even when you get some wrong.</p>
          </div>
        </header>

        <Card className="stack-lg">
          <div className="setup-row">
            <div>
              <h3>Questions</h3>
              <p className="small muted">
                <Clock size={13} aria-hidden style={{ verticalAlign: "-2px" }} /> about {Math.max(2, Math.round(count * 0.6))} minutes
              </p>
            </div>
            <div className="row" role="radiogroup" aria-label="Number of questions">
              {COUNTS.map((c) => (
                <button key={c} type="button" role="radio" aria-checked={count === c} className={cx("count-option", count === c && "selected")} onClick={() => setCount(c)}>
                  {c}
                </button>
              ))}
            </div>
          </div>

          <div className="setup-row">
            <div>
              <h3>Difficulty</h3>
              <p className="small muted">Mixed is a great default: a few easy wins and some stretch.</p>
            </div>
            <Segmented
              label="Difficulty"
              value={difficulty}
              onChange={setDifficulty}
              options={[
                { value: "EASY", label: "Easy" },
                { value: "MIXED", label: "Mixed" },
                { value: "HARD", label: "Hard" },
              ]}
            />
          </div>

          <div className="stack-sm">
            <div className="row-between">
              <div>
                <h3>Topics</h3>
                <p className="small muted">
                  <Shuffle size={13} aria-hidden style={{ verticalAlign: "-2px" }} /> {selected.size === 0 ? "All topics, interleaved" : `${selected.size} selected`}
                </p>
              </div>
              {weak.length > 0 && (
                <Button size="sm" variant="soft" onClick={() => setSelected(new Set(weak.map((t) => t.id)))}>
                  Focus on topics that need practice
                </Button>
              )}
            </div>
            {!material ? (
              <Skeleton height={80} />
            ) : (
              <div className="row">
                <button type="button" className="chip" aria-pressed={selected.size === 0} onClick={() => setSelected(new Set())}>
                  All topics
                </button>
                {material.topics.map((t) => (
                  <button key={t.id} type="button" className="chip" aria-pressed={selected.has(t.id)} onClick={() => toggle(t.id)}>
                    {t.title}
                  </button>
                ))}
              </div>
            )}
          </div>

          {error && <Alert>{error}</Alert>}
          <div className="row" style={{ justifyContent: "flex-end" }}>
            <Button variant="primary" size="lg" icon={<Brain size={18} />} onClick={start} disabled={!material}>
              Start test
            </Button>
          </div>
        </Card>
      </div>
    </PageTransition>
  );
}
