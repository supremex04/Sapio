import { useEffect, useRef, useState } from "react";
import type { GLViewer } from "3dmol";
import type { SceneClock } from "./NarratedScene";
import type { MoleculeScene } from "./types";
import { hasWebGL } from "./webgl";

const BASE = { stick: { radius: 0.15, colorscheme: "Jmol" }, sphere: { scale: 0.24, colorscheme: "Jmol" } };
/** The rest, while a part is pointed at: thinner and faded, still in its element colours. */
const DIMMED = { stick: { radius: 0.1, colorscheme: "Jmol", opacity: 0.45 }, sphere: { scale: 0.18, colorscheme: "Jmol", opacity: 0.45 } };
/** The part pointed at keeps its element colours (so a nitrogen still reads as one), larger, inside a glow. */
const FOCUSED = { stick: { radius: 0.2, colorscheme: "Jmol" }, sphere: { scale: 0.3, colorscheme: "Jmol" } };
const GLOW = { color: "#facc15", opacity: 0.35 };

/**
 * A molecule in 3D (3Dmol.js): the beat's atoms glow and the camera turns to them; drag to rotate, scroll to zoom.
 * Without WebGL: RDKit's 2D drawing of each beat, with the same atoms highlighted.
 */
export function MoleculeView({ scene, clock }: { scene: MoleculeScene; clock: SceneClock }) {
  const host = useRef<HTMLDivElement>(null);
  const viewer = useRef<GLViewer | null>(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(() => !hasWebGL());

  useEffect(() => {
    if (!hasWebGL()) return;
    let alive = true;
    import("3dmol")
      .then(($3Dmol) => {
        if (!alive || !host.current) return;
        const v = $3Dmol.createViewer(host.current, { backgroundColor: "#0b1020", antialias: true });
        v.addModel(scene.molBlock, "sdf");
        v.setStyle({}, BASE);
        v.zoomTo();
        v.render();
        viewer.current = v;
        setReady(true);
      })
      .catch(() => alive && setFailed(true));
    return () => {
      alive = false;
      viewer.current?.clear();
      viewer.current = null;
    };
  }, [scene.molBlock]);

  const atoms = scene.highlights[clock.beat] ?? [];
  const key = atoms.join(",");
  useEffect(() => {
    const v = viewer.current;
    if (!v || !ready) return;
    v.removeAllShapes();
    if (atoms.length) {
      v.setStyle({}, DIMMED);
      v.setStyle({ index: atoms }, FOCUSED);
      for (const atom of v.selectedAtoms({ index: atoms })) {
        v.addSphere({ center: { x: atom.x ?? 0, y: atom.y ?? 0, z: atom.z ?? 0 }, radius: atom.elem === "H" ? 0.45 : 0.75, ...GLOW });
      }
      v.zoomTo({ index: atoms }, 700);
    } else {
      v.setStyle({}, BASE);
      v.zoomTo({}, 700);
    }
    v.render();
    // eslint-disable-next-line react-hooks/exhaustive-deps -- atoms changes identity each render; key is its value
  }, [key, ready]);

  // A slow turn while nothing in particular is pointed at, so the shape reads as 3D.
  useEffect(() => {
    const v = viewer.current;
    if (!v || !ready) return;
    v.spin(clock.playing && atoms.length === 0 ? "y" : false, 0.4);
  }, [clock.playing, key, ready, atoms.length]);

  if (failed) {
    const drawing = scene.drawings?.[clock.beat] ?? scene.drawings?.[0];
    return drawing ? (
      <div className="viz-flat">
        <img src={`data:image/png;base64,${drawing}`} alt={`Structural formula of ${scene.name}${atoms.length ? ", the part this step is about highlighted" : ""}`} />
        <NoWebGL />
      </div>
    ) : (
      <p className="viz-view-error">The 3D viewer could not start in this browser.</p>
    );
  }
  return <div ref={host} className="viz-3d" aria-label={`3D model of ${scene.name}. Drag to rotate, scroll to zoom.`} role="img" />;
}

/** Why the 3D view is missing, and how to get it. */
export function NoWebGL() {
  return <p className="viz-flat-note xs">3D needs WebGL, which this browser has turned off. In Chrome: Settings, System, "Use graphics acceleration when available", then relaunch.</p>;
}
