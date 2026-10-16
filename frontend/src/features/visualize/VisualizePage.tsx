import { Wand2 } from "lucide-react";
import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Badge } from "../../shared/ui/Badge";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { EmptyState } from "../../shared/ui/EmptyState";
import { PageTransition } from "../../shared/ui/PageTransition";
import { ProgressBar } from "../../shared/ui/Progress";
import { Segmented } from "../../shared/ui/Segmented";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { formatRelative } from "../../shared/lib/format";
import { visualizeApi } from "./api";
import { fraction, stage, videoLength } from "./progress";
import { isWorking, type Engine, type Visualization } from "./types";
import "./visualize.css";

/** Starting points: one per kind of thing the studio shows well. */
const EXAMPLES: { text: string; engine: Engine }[] = [
  { text: "a^2 + b^2 = c^2", engine: "AUTO" },
  { text: "The derivative as the slope of a tangent line", engine: "AUTO" },
  { text: "Simple harmonic motion: x(t) = A cos(wt)", engine: "AUTO" },
  { text: "Why does caffeine keep you awake? Show the molecule", engine: "AUTO" },
  { text: "How haemoglobin carries oxygen", engine: "AUTO" },
  { text: "def fib(n):\n    if n < 2:\n        return n\n    return fib(n - 1) + fib(n - 2)\n\nprint(fib(4))", engine: "AUTO" },
];

export const ENGINE_LABEL: Record<Engine, string> = {
  AUTO: "Auto",
  ANIMATION: "Animation",
  MOLECULE: "Molecule",
  PROTEIN: "Protein",
  CODE: "Code",
};

const ENGINE_HINT: Record<Engine, string> = {
  AUTO: "Sapio picks: an animation, a 3D molecule or protein, or a program run step by step.",
  ANIMATION: "An animated video: formulas, graphs, geometry, physics, processes.",
  MOLECULE: "A molecule in 3D, from its name or a SMILES string.",
  PROTEIN: "A protein or DNA structure from the Protein Data Bank, by name or ID (e.g. 4HHB).",
  CODE: "Paste Python (or describe an algorithm) and watch it run line by line.",
};

const POLL_MS = 3000;
export const MAX_REQUEST = 4000;

/** Ask for a formula or an idea to be animated; past visualizations below, updating while they are made. */
export function VisualizePage() {
  const [items, setItems] = useState<Visualization[] | null>(null);
  const [request, setRequest] = useState("");
  const [engine, setEngine] = useState<Engine>("AUTO");
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);
  const navigate = useNavigate();
  const input = useRef<HTMLTextAreaElement>(null);

  const load = useCallback(() => visualizeApi.list().then(setItems, (e) => setError(errorMessage(e))), []);
  useEffect(() => {
    void load();
  }, [load]);

  const working = items?.some(isWorking) ?? false;
  useEffect(() => {
    if (!working) return;
    const timer = window.setInterval(() => void load(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [working, load]);

  async function create() {
    const text = request.trim();
    if (!text) return;
    setBusy(true);
    setError(null);
    try {
      const v = await visualizeApi.create(text, engine);
      navigate(`/visualize/${v.id}`);
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="page-head">
          <h1 className="row">
            <Emoji name="visualize" size={40} /> Visualize
          </h1>
          <p className="muted">Type a formula, a molecule, a protein or some code, or describe an idea. Sapio picks the best way to show it and narrates it for you.</p>
        </header>
        {error && <Alert>{error}</Alert>}

        <Card>
          <label className="field">
            <span className="field-label">What should we visualize?</span>
            <textarea
              ref={input}
              className="input viz-input"
              rows={3}
              maxLength={MAX_REQUEST}
              value={request}
              onChange={(e) => setRequest(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === "Enter" && (e.metaKey || e.ctrlKey)) void create();
              }}
              placeholder={"F = ma, aspirin, 4HHB, or paste some Python"}
            />
          </label>
          <div className="viz-examples" aria-label="Examples">
            {EXAMPLES.map((ex) => (
              <button
                key={ex.text}
                type="button"
                className="chip"
                onClick={() => {
                  setRequest(ex.text);
                  setEngine(ex.engine);
                  input.current?.focus();
                }}
              >
                {ex.text.split("\n")[0]}
              </button>
            ))}
          </div>
          <div className="viz-engine">
            <Segmented<Engine>
              label="How to show it"
              value={engine}
              onChange={setEngine}
              options={(Object.keys(ENGINE_LABEL) as Engine[]).map((e) => ({ value: e, label: ENGINE_LABEL[e] }))}
            />
            <span className="xs muted">{ENGINE_HINT[engine]}</span>
          </div>
          <div className="row-between viz-submit">
            <span className="xs muted">Animations take about a minute; molecules, proteins and code a few seconds.</span>
            <Button variant="primary" icon={<Wand2 size={16} />} onClick={() => void create()} loading={busy} disabled={!request.trim()}>
              Visualize
            </Button>
          </div>
        </Card>

        <section className="stack">
          <h2 className="card-title">Your visualizations</h2>
          {!items && <SkeletonText lines={3} />}
          {items && items.length === 0 && (
            <EmptyState title="Nothing animated yet" mood="encourage">
              Pick an example above to see what a visualization looks like.
            </EmptyState>
          )}
          {items && items.length > 0 && (
            <ul className="viz-grid">
              {items.map((v) => (
                <li key={v.id}>
                  <VisualizationCard v={v} />
                </li>
              ))}
            </ul>
          )}
        </section>
      </div>
    </PageTransition>
  );
}

function VisualizationCard({ v }: { v: Visualization }) {
  return (
    <Link to={`/visualize/${v.id}`} className="viz-card card card-interactive">
      <div className="viz-thumb">
        {v.thumbnailUrl ? (
          <img src={v.thumbnailUrl} alt="" loading="lazy" />
        ) : (
          <span className="viz-thumb-empty" aria-hidden>
            <Emoji name="visualize" size={44} />
          </span>
        )}
        {v.status === "READY" && v.durationSeconds != null && <span className="viz-length">{videoLength(v.durationSeconds)}</span>}
        {v.engine !== "AUTO" && v.engine !== "ANIMATION" && <span className="viz-engine-tag">{ENGINE_LABEL[v.engine]}</span>}
      </div>
      <div className="viz-card-body">
        <strong className="viz-card-title">{v.title ?? v.request.split("\n")[0]}</strong>
        {isWorking(v) ? (
          <>
            <span className="xs muted">{stage(v)}</span>
            <ProgressBar value={fraction(v)} size="sm" label={stage(v)} />
          </>
        ) : (
          <span className="row xs muted">
            {v.status === "FAILED" && <Badge tone="danger">Failed</Badge>}
            {formatRelative(v.completedAt ?? v.createdAt)}
          </span>
        )}
      </div>
    </Link>
  );
}
