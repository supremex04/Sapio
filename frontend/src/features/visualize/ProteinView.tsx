import { useEffect, useRef, useState } from "react";
import type { AtomSelectionSpec, GLViewer } from "3dmol";
import { chainColour } from "./chains";
import { NoWebGL } from "./MoleculeView";
import { proteinFocus } from "./progress";
import type { SceneClock } from "./NarratedScene";
import type { Beat, ProteinScene } from "./types";
import { hasWebGL } from "./webgl";

/**
 * A structure from the Protein Data Bank (3Dmol.js): each chain in its own colour (the legend says which is which),
 * ligands as sticks. A beat's focus comes forward with what surrounds it, labelled with the residue names from the
 * structure. Only foci checked against the structure are shown. Without WebGL: the Protein Data Bank's picture,
 * with the focus named.
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
        base(v, scene);
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
  }, [structureUrl, scene]);

  // Checked foci when the scene has them (older visualizations: the storyboard's own).
  const focus = scene.focus ? (scene.focus[clock.beat] ?? "") : (beats[clock.beat]?.focus ?? "");
  const label = scene.labels ? (scene.labels[clock.beat] ?? null) : null;
  useEffect(() => {
    const v = viewer.current;
    if (!v || !ready) return;
    base(v, scene);
    v.removeAllLabels();
    const sel = proteinFocus(focus) as AtomSelectionSpec | null;
    if (sel) {
      v.addStyle(sel, { stick: { colorscheme: "yellowCarbon", radius: 0.28 } });
      const small = !!sel.resn || ((sel as { resi?: unknown }).resi !== undefined && v.selectedAtoms(sel).length < 200);
      if (small) {
        v.addResLabels(sel, { font: "sans-serif", fontSize: 14, fontColor: "white", backgroundColor: "black", backgroundOpacity: 0.6, showBackground: true });
        // The part and what surrounds it, not so close that a nearby helix fills the frame.
        v.zoomTo({ within: { distance: 9, sel } } as AtomSelectionSpec, 800);
      } else {
        v.zoomTo(sel, 800);
      }
    } else {
      v.zoomTo({}, 800);
    }
    v.render();
  }, [focus, ready, scene]);

  const corner = (
    <p className="viz-corner xs">
      {scene.name} ({scene.pdbId}){label ? <span className="viz-corner-focus"> · {label}</span> : null}
    </p>
  );

  if (failed) {
    return (
      <div className="viz-flat">
        {picture ? <img src={picture} alt={`${scene.name} (${scene.pdbId}), as pictured by the Protein Data Bank`} /> : <p className="viz-view-error">The 3D viewer could not start in this browser.</p>}
        {(label || focus) && <p className="viz-flat-focus small">Now: {label ?? describeFocus(focus)}</p>}
        <NoWebGL />
      </div>
    );
  }
  return (
    <>
      <div ref={host} className="viz-3d" role="img" aria-label={`3D structure of ${scene.name} (${scene.pdbId}). Drag to rotate, scroll to zoom.`} />
      {corner}
    </>
  );
}

function base(v: GLViewer, scene: ProteinScene) {
  v.setStyle({}, { cartoon: { color: "#94a3b8" } });
  (scene.chains ?? []).forEach((c, i) => v.setStyle({ chain: c.id }, { cartoon: { color: chainColour(i) } }));
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
