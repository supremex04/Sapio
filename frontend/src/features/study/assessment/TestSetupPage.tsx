import { ArrowLeft } from "lucide-react";
import { useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { ApiError, errorMessage } from "../../../shared/api/errors";
import { Dia } from "../../../shared/illustrations/Dia";
import { Emoji } from "../../../shared/illustrations/Emoji";
import type { IconName } from "../../../shared/illustrations/Emoji";
import { Alert } from "../../../shared/ui/Alert";
import { Button } from "../../../shared/ui/Button";
import { Chip } from "../../../shared/ui/Chip";
import { cx } from "../../../shared/ui/cx";
import { PageTransition } from "../../../shared/ui/PageTransition";
import { Skeleton } from "../../../shared/ui/Skeleton";
import { SpeechBubble } from "../../../shared/ui/SpeechBubble";
import { studyApi } from "../api";
import { GeneratingState } from "../components/GeneratingState";
import { testMinutes } from "../nextStep";
import type { Difficulty, Material } from "../types";
import "./assessment.css";

const COUNTS = [5, 10, 15, 20] as const;
const DIFFICULTIES: { value: Difficulty; label: string; emoji: IconName; note: string }[] = [
  { value: "EASY", label: "Easy", emoji: "seedling", note: "Warm up" },
  { value: "MIXED", label: "Mixed", emoji: "high-voltage", note: "Best default" },
  { value: "HARD", label: "Hard", emoji: "fire", note: "Stretch yourself" },
];

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
  const difficultyLabel = DIFFICULTIES.find((d) => d.value === difficulty)?.label ?? "";

  return (
    <PageTransition>
      <div className="ts">
        <Link to={`/documents/${id}`} className="ts-back">
          <ArrowLeft size={18} strokeWidth={2.5} aria-hidden /> Study guide
        </Link>

        <header className="ts-head">
          <p className="eyebrow">Test my knowledge{material ? ` · ${material.title}` : ""}</p>
          <h1>Set up your test</h1>
          <div className="ts-dia">
            <Dia mood="happy" size={80} />
            <SpeechBubble>Recalling answers strengthens memory far more than re-reading, even the misses help!</SpeechBubble>
          </div>
        </header>

        <section className="ts-section" aria-labelledby="ts-count">
          <h2 id="ts-count">How many questions?</h2>
          <div className="ts-tiles ts-tiles-4" role="radiogroup" aria-labelledby="ts-count">
            {COUNTS.map((c) => (
              <button key={c} type="button" role="radio" aria-checked={count === c} className={cx("ts-tile", count === c && "is-selected")} onClick={() => setCount(c)}>
                <span className="ts-tile-big">{c}</span>
                <span className="ts-tile-note">~{testMinutes(c)} min</span>
              </button>
            ))}
          </div>
        </section>

        <section className="ts-section" aria-labelledby="ts-difficulty">
          <h2 id="ts-difficulty">Difficulty</h2>
          <div className="ts-tiles ts-tiles-3" role="radiogroup" aria-labelledby="ts-difficulty">
            {DIFFICULTIES.map((d) => (
              <button key={d.value} type="button" role="radio" aria-checked={difficulty === d.value} className={cx("ts-tile", difficulty === d.value && "is-selected")} onClick={() => setDifficulty(d.value)}>
                <Emoji name={d.emoji} size={36} />
                <span className="ts-tile-label">{d.label}</span>
                <span className="ts-tile-note">{d.note}</span>
              </button>
            ))}
          </div>
        </section>

        <section className="ts-section" aria-labelledby="ts-topics">
          <div className="row-between">
            <h2 id="ts-topics">Topics</h2>
            {weak.length > 0 && (
              <Button size="sm" variant="soft" onClick={() => setSelected(new Set(weak.map((t) => t.id)))}>
                Focus on weak topics
              </Button>
            )}
          </div>
          {!material ? (
            <Skeleton height={80} />
          ) : (
            <div className="row">
              <Chip emoji="card-index-dividers" pressed={selected.size === 0} onClick={() => setSelected(new Set())}>
                All topics
              </Chip>
              {material.topics.map((t) => (
                <Chip key={t.id} pressed={selected.has(t.id)} onClick={() => toggle(t.id)}>
                  {t.title}
                </Chip>
              ))}
            </div>
          )}
        </section>

        {error && <Alert>{error}</Alert>}

        <div className="ts-start">
          <p className="ts-summary">
            {count} questions · {difficultyLabel} · {selected.size === 0 ? "all topics, mixed together" : `${selected.size} topic${selected.size === 1 ? "" : "s"}`} · about {testMinutes(count)} min
          </p>
          <Button variant="mastery" size="lg" block onClick={start} disabled={!material}>
            Start test
          </Button>
        </div>
      </div>
    </PageTransition>
  );
}
