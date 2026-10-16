import { ArrowLeft, RotateCcw, Trash2 } from "lucide-react";
import { useCallback, useEffect, useState } from "react";
import { Link, useNavigate, useParams } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { ConfirmButton } from "../../shared/ui/ConfirmButton";
import { Markdown } from "../../shared/ui/Markdown";
import { PageTransition } from "../../shared/ui/PageTransition";
import { ProgressBar } from "../../shared/ui/Progress";
import { SkeletonText } from "../../shared/ui/Skeleton";
import { Sage } from "../../shared/illustrations/Sage";
import { visualizeApi } from "./api";
import { CodeView } from "./CodeView";
import { MoleculeView } from "./MoleculeView";
import { NarratedScene } from "./NarratedScene";
import { ProteinView } from "./ProteinView";
import { VideoPlayer } from "./VideoPlayer";
import { fraction, stage, videoLength } from "./progress";
import { isWorking, type CodeScene, type MoleculeScene, type ProteinScene, type Visualization } from "./types";
import "./visualize.css";

const POLL_MS = 2500;

/** One visualization: the video once drawn (progress until then), its storyboard, and the code behind it. */
export function VisualizationPage() {
  const { id = "" } = useParams();
  const [v, setV] = useState<Visualization | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [retrying, setRetrying] = useState(false);
  const navigate = useNavigate();

  const load = useCallback(() => visualizeApi.get(id).then(setV, (e) => setError(errorMessage(e))), [id]);
  useEffect(() => {
    void load();
  }, [load]);

  const working = v != null && isWorking(v);
  useEffect(() => {
    if (!working) return;
    const timer = window.setInterval(() => void load(), POLL_MS);
    return () => window.clearInterval(timer);
  }, [working, load]);

  async function retry() {
    setRetrying(true);
    setError(null);
    try {
      setV(await visualizeApi.retry(id));
    } catch (e) {
      setError(errorMessage(e));
    } finally {
      setRetrying(false);
    }
  }

  async function remove() {
    try {
      await visualizeApi.remove(id);
      navigate("/visualize");
    } catch (e) {
      setError(errorMessage(e));
    }
  }

  return (
    <PageTransition>
      <div className="stack-lg viz-detail">
        <Link to="/visualize" className="row small muted viz-back">
          <ArrowLeft size={16} aria-hidden /> All visualizations
        </Link>
        {error && <Alert>{error}</Alert>}
        {!v && !error && <SkeletonText lines={6} />}
        {v && (
          <>
            <header className="page-head">
              <h1>{v.title ?? "Visualization"}</h1>
              {/* Code is shown in the viewer, line by line; as Markdown it would collapse into one line. */}
              {v.engine !== "CODE" && <Markdown className="viz-request muted">{v.request}</Markdown>}
            </header>

            {v.status === "READY" && v.scene ? (
              <SceneFor v={v} />
            ) : (
            <div className="viz-stage">
              {v.status === "READY" && v.videoUrl ? (
                <VideoPlayer src={v.videoUrl} poster={v.thumbnailUrl ?? undefined} narrated={v.narrated} />
              ) : working ? (
                <div className="viz-making" role="status" aria-live="polite">
                  <Sage mood="thinking" size={88} />
                  <strong>{stage(v)}</strong>
                  <ProgressBar value={fraction(v)} label={stage(v)} />
                  <span className="xs muted">This usually takes under a minute. You can leave this page; it keeps going.</span>
                </div>
              ) : (
                <div className="viz-making">
                  <Sage mood="oops" size={88} />
                  <strong>This one could not be made</strong>
                  <span className="small muted">{v.error}</span>
                </div>
              )}
            </div>
            )}

            {v.status === "READY" && v.engine === "MOLECULE" && v.scene && <MoleculeFacts scene={v.scene as MoleculeScene} drawing={v.thumbnailUrl} />}
            {v.status === "READY" && v.engine === "PROTEIN" && v.scene && <ProteinFacts scene={v.scene as ProteinScene} />}

            <div className="row-between">
              <span className="small muted">
                {v.status === "READY" && v.durationSeconds != null && `${videoLength(v.durationSeconds)} long${v.narrated ? ", narrated" : ""}`}
                {v.attempts > 1 && v.status === "READY" && ` · drawn on try ${v.attempts}`}
              </span>
              <div className="row">
                {v.status === "FAILED" && (
                  <Button variant="primary" icon={<RotateCcw size={16} />} onClick={() => void retry()} loading={retrying}>
                    Try again
                  </Button>
                )}
                {!working && (
                  <ConfirmButton onConfirm={remove} prompt="Delete this visualization?" confirmLabel="Delete" icon={<Trash2 size={16} />}>
                    Delete
                  </ConfirmButton>
                )}
              </div>
            </div>

            {v.beats.length > 0 && (
              <Card>
                <h2 className="card-title row">
                  <Emoji name="scroll" size={24} /> Storyboard
                </h2>
                <ol className="viz-beats">
                  {v.beats.map((b, i) => (
                    <li key={i}>
                      <Markdown className="viz-beat-shows">{b.shows}</Markdown>
                      {b.says && <Markdown className="viz-beat-says small muted">{b.says}</Markdown>}
                    </li>
                  ))}
                </ol>
              </Card>
            )}

            {v.code && v.engine === "ANIMATION" && (
              <details className="viz-code card">
                <summary>The code behind it (Python, Manim)</summary>
                <pre>
                  <code>{v.code}</code>
                </pre>
              </details>
            )}
          </>
        )}
      </div>
    </PageTransition>
  );
}

/** The narrated viewer of a molecule, protein or program. */
function SceneFor({ v }: { v: Visualization }) {
  const scene = v.scene;
  if (!scene) return null;
  return (
    <NarratedScene audioUrl={v.audioUrl} timing={scene.timing} beats={v.beats}>
      {(clock) =>
        v.engine === "MOLECULE" ? (
          <MoleculeView scene={scene as MoleculeScene} clock={clock} />
        ) : v.engine === "PROTEIN" && v.structureUrl ? (
          <ProteinView scene={scene as ProteinScene} beats={v.beats} structureUrl={v.structureUrl} picture={v.thumbnailUrl} clock={clock} />
        ) : (
          <CodeView scene={scene as CodeScene} beats={v.beats} clock={clock} />
        )
      }
    </NarratedScene>
  );
}

function MoleculeFacts({ scene, drawing }: { scene: MoleculeScene; drawing: string | null }) {
  return (
    <Card>
      <div className="viz-facts">
        {drawing && <img className="viz-drawing" src={drawing} alt={`Structural formula of ${scene.name}`} />}
        <dl className="viz-fact-list">
          <div>
            <dt>Molecule</dt>
            <dd>{scene.name}</dd>
          </div>
          <div>
            <dt>Formula</dt>
            <dd>{scene.formula}</dd>
          </div>
          <div>
            <dt>Molar mass</dt>
            <dd>{scene.weight} g/mol</dd>
          </div>
          <div>
            <dt>SMILES</dt>
            <dd>
              <code>{scene.smiles}</code>
            </dd>
          </div>
          {!scene.threeD && (
            <div>
              <dt>Shape</dt>
              <dd className="muted">A 3D shape could not be computed, so the drawing is flat.</dd>
            </div>
          )}
        </dl>
      </div>
    </Card>
  );
}

function ProteinFacts({ scene }: { scene: ProteinScene }) {
  return (
    <Card>
      <dl className="viz-fact-list">
        <div>
          <dt>Structure</dt>
          <dd>{scene.name}</dd>
        </div>
        <div>
          <dt>Protein Data Bank</dt>
          <dd>
            <a href={`https://www.rcsb.org/structure/${scene.pdbId}`} target="_blank" rel="noreferrer noopener">
              {scene.pdbId}
            </a>{" "}
            <span className="muted small">{scene.entryTitle}</span>
          </dd>
        </div>
      </dl>
    </Card>
  );
}
