export type VisualizationStatus = "PLANNING" | "CODING" | "RENDERING" | "READY" | "FAILED";
/** How it is shown: AUTO lets Sapio choose (rules, then the storyboard). */
export type Engine = "AUTO" | "ANIMATION" | "MOLECULE" | "PROTEIN" | "CODE";

/** One scene of the storyboard: what appears on screen, and the line of narration under it. */
export interface Beat {
  shows: string;
  says: string;
  /** What to point at: SMARTS (molecule), "A:58-63" or "ligand:HEM" (protein), "3-5" (code lines). */
  focus?: string;
}

/** Where each beat's line starts in the narration track. */
export interface Timing {
  start: number;
  seconds: number;
}

export interface MoleculeScene {
  name: string;
  smiles: string;
  formula: string;
  weight: number;
  heavyAtoms: number;
  threeD: boolean;
  molBlock: string;
  /** Per beat: atom indices to highlight. */
  highlights: number[][];
  /** Per beat: a 2D drawing (PNG, base64) with those atoms highlighted, for browsers without WebGL. */
  drawings?: string[];
  timing: Timing[];
}

export interface ProteinScene {
  pdbId: string;
  name: string;
  entryTitle: string;
  /** Per beat: the focus checked against the structure ("B:92", "ligand:HEM", "" when it could not be confirmed). */
  focus?: string[];
  /** Per beat: what the focus is, from the structure ("His92, chain B (hemoglobin subunit beta)"). */
  labels?: (string | null)[];
  /** The polymer chains and what each is, as the entry names them. */
  chains?: { id: string; description: string }[];
  timing: Timing[];
}

export interface Frame {
  name: string;
  line: number;
  vars: Record<string, string>;
}

export interface TraceStep {
  /** 0 for the end step. */
  line: number;
  /** end: the program finished; its final output and variables. */
  event: "line" | "return" | "exception" | "end";
  stack: Frame[];
  output: string;
  value?: string;
}

export interface CodeScene {
  program: string;
  trace: { steps: TraceStep[]; output: string; error: string | null; truncated: boolean };
  /** Per beat: the lines it explains ("3-6"), found in the program traced; "" when they could not be. */
  lines?: string[];
  timing: Timing[];
}

export interface Visualization {
  id: string;
  request: string;
  documentId: string | null;
  status: VisualizationStatus;
  engineChoice: Engine;
  /** The engine asked for until planned, then the one used. */
  engine: Engine;
  title: string | null;
  kind: string | null;
  beats: Beat[];
  attempts: number;
  error: string | null;
  durationSeconds: number | null;
  /** The video speaks each beat's caption. */
  narrated: boolean;
  /** Signed, expiring; null until READY. */
  videoUrl: string | null;
  thumbnailUrl: string | null;
  /** Narration track of a molecule, protein or code visualization. */
  audioUrl: string | null;
  /** A protein's structure (mmCIF). */
  structureUrl: string | null;
  /** What the viewer shows; only on a single visualization. */
  scene: MoleculeScene | ProteinScene | CodeScene | null;
  createdAt: string;
  completedAt: string | null;
}

export const isWorking = (v: Visualization) => v.status === "PLANNING" || v.status === "CODING" || v.status === "RENDERING";
