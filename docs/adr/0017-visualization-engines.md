# 0017. Visualization engines, routed by rules and by the storyboard call

- Status: Accepted
- Date: 2026-10-16

## Context

Visualizations ([ADR-0016](0016-visualizations.md)) drew everything as an animation: the AI writes Manim code and
the renderer turns it into video. That suits formulas, graphs and physics, but chemistry, biology and code are
better shown with tools made for them, and writing scene code for them costs tokens, CPU and failed renders. We
considered RDKit and PyMOL (chemistry, biology) and VPython (physics). The engine has to be chosen without an extra
AI call.

## Decision

- **Four engines.** `ANIMATION` (Manim, as before); `MOLECULE`: RDKit builds a small molecule from SMILES (3D
  shape by ETKDG and MMFF, a 2D drawing, formula, mass) and highlights each beat's atoms from a SMARTS pattern;
  `PROTEIN`: an entry from the RCSB Protein Data Bank, fetched once by the backend and stored; `CODE`: a short
  Python program run under `sys.settrace` in the renderer's sandbox, recording every line, the call stack with
  each frame's variables, and the output.
- **The new engines write no code.** The storyboard returns their data in the same call: a SMILES string, a PDB
  ID, or a program, and a per-beat `focus` (SMARTS, `A:58-63` / `ligand:HEM`, or line ranges). RDKit, the PDB and
  the tracer are deterministic, so there is no repair loop, and each takes milliseconds to a few seconds instead
  of a minute of rendering. The learner's own SMILES, PDB ID or program is used before the AI's.
- **Shown in the browser, narrated as a track.** Each caption is spoken (Kokoro, as for videos) into one audio
  track with the start of each beat; the viewer follows it: 3Dmol.js for molecules and proteins (rotatable, the
  beat's atoms or residues brought forward), a step-through listing for code. Browsers without WebGL get RDKit's
  2D drawing of each beat with its atoms highlighted, or the PDB's picture with the focus named.
- **Routing without an extra AI call**, cheapest first: the learner's choice (Auto, Animation, Molecule, Protein,
  Code); rules in the backend for obvious requests (pasted code; a SMILES string, only once RDKit reads it; a PDB
  ID); otherwise the storyboard call, which happens anyway, picks the engine as part of planning. A choice the AI
  cannot fill (no molecule, no such PDB entry) falls back to an animation when the engine was not chosen by the
  learner, and fails with the reason when it was.
- **Not taken:** PyMOL (a heavy install whose strength, rendered protein images, a browser viewer matches at no
  server cost) and VPython (meant for live 3D in a browser; recording it needs a headless browser with WebGL).
  Interactive physics, if wanted, belongs in the browser as parameterised templates.

## Consequences

- A molecule, protein or program costs one AI call and seconds of CPU; animations are unchanged.
- The tracer runs untrusted code like scenes do: an AST allowlist (teaching modules, a few dunders such as
  `__init__`, `input()` from the request), an empty environment, CPU, memory and process limits, a 400-step cap
  enforced by the tracer itself, and the renderer's lack of secrets and internet. Writing this check closed a gap
  in the scene check too: dunder names such as `__builtins__` were not refused.
- SMARTS from the AI is unreliable: it disagrees with RDKit's aromaticity (caffeine's carbonyl carbons and nitrogens
  are aromatic) or is far too broad (`[#6][#7]` lit up every carbon and nitrogen while the narration said "three
  methyl groups"). So the renderer keeps a list of common groups whose patterns match only the group's own atoms,
  which a focus can name (`group:methyl`); a SMARTS that lights up nothing or more than 60% of the molecule gives
  way to a group the caption names; other patterns that match nothing are retried by element and connectivity. A
  group's hydrogens light up with it in 3D, and highlighted atoms keep their element colours inside a glow.
- The AI's pointers into proteins and code are checked against what is shown. A protein focus is read against the
  structure's own residues, ligands and chains (mmCIF): a residue that is not what the focus or caption names is
  moved to the chain where it is (alpha 92 is arginine; the proximal histidine is beta 92 or alpha 87), and
  dropped when that is not unambiguous; the viewer labels the part from the structure ("His87, chain A (hemoglobin
  subunit alpha)") and names the entry and its state (deoxy or oxy). Code is pointed at by the text of its lines,
  found in the program actually traced, since the AI miscounts line numbers.
- The backend now calls the RCSB (public) for protein entries; structures over 15 MiB are refused.
- Later: other languages in the tracer, more than one molecule (reactions), physics templates in the browser.
