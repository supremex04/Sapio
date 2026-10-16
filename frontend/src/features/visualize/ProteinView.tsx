import { useEffect, useRef, useState } from "react";
import type { AtomSelectionSpec, GLViewer } from "3dmol";
import { proteinFocus } from "./progress";
import type { SceneClock } from "./NarratedScene";
import type { Beat, ProteinScene } from "./types";
import { NoWebGL } from "./MoleculeView";
import { hasWebGL } from "./webgl";

/**
 * A structure from the Protein Data Bank (3Dmol.js): cartoon, ligands as sticks; the beat's focus comes forward.
 * Without WebGL: the Protein Data Bank's picture of it, with what the step points at named.
 */
export function ProteinView({ scene, beats, structureUrl, picture, clock }: { scene: ProteinScene; beats: Beat[]; structureUrl: string; picture: string | null; clock: SceneClock }) {
  const host = useRef<HTMLDivElement>(null);
  const viewer = useRef<GLViewer | null>(null);
  const [ready, setReady] = useState(false);
  const [failed, setFailed] = useState(() => !hasWebGL());

  useEffect(() => {
    if (!hasWebGL()) return;
    let alive = true;
    Promise.all([import("3dmol"), fetch(structureUrl).then((r) => (r.ok ? r.text() : Promise.reject(new Error(String(r.status)))))])
      .then(([$3Dmol, cif]) => {
        if (!alive || !host.current) return;
        const v = $3Dmol.createViewer(host.current, { backgroundColor: "#0b1020", antialias: true });
        v.addModel(cif, "cif");
        base(v);
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
  }, [structureUrl]);

  const focus = beats[clock.beat]?.focus ?? "";
  useEffect(() => {
    const v = viewer.current;
    if (!v || !ready) return;
    base(v);
    const sel = proteinFocus(focus) as AtomSelectionSpec | null;
    if (sel) {
      v.addStyle(sel, { stick: { colorscheme: "yellowCarbon", radius: 0.25 } });
      v.zoomTo(sel, 800);
    } else {
      v.zoomTo({}, 800);
    }
    v.render();
  }, [focus, ready]);

  if (failed) {
    return (
      <div className="viz-flat">
        {picture ? <img src={picture} alt={`${scene.name} (${scene.pdbId}), as pictured by the Protein Data Bank`} /> : <p className="viz-view-error">The 3D viewer could not start in this browser.</p>}
        {focus && <p className="viz-flat-focus small">Now: {describeFocus(focus)}</p>}
        {hasWebGL() ? null : <NoWebGL />}
      </div>
    );
  }
  return <div ref={host} className="viz-3d" role="img" aria-label={`3D structure of ${scene.name} (${scene.pdbId}). Drag to rotate, scroll to zoom.`} />;
}

function base(v: GLViewer) {
  v.setStyle({}, { cartoon: { color: "spectrum" } });
  v.setStyle({ hetflag: true }, { stick: { radius: 0.2 } });
  v.setStyle({ resn: "HOH" }, {}); // no water
  v.setStyle({ resn: "WAT" }, {});
}

/** "A:58-63" as "chain A, residues 58-63"; "ligand:HEM" as "the HEM group". */
function describeFocus(focus: string): string {
  const sel = proteinFocus(focus);
  if (!sel) return focus;
  if (sel.resn) return `the ${sel.resn} group`;
  return `chain ${sel.chain}${sel.resi ? `, residue${sel.resi.length > 1 || sel.resi[0]?.includes("-") ? "s" : ""} ${sel.resi.join(", ")}` : ""}`;
}
