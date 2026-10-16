import type { Visualization } from "./types";

/** What the learner reads while a visualization is being made. */
export function stage(v: Visualization): string {
  switch (v.status) {
    case "PLANNING":
      return "Planning the scenes";
    case "CODING":
      return "Writing the animation";
    case "RENDERING":
      if (v.engine === "MOLECULE") return "Building the molecule";
      if (v.engine === "PROTEIN") return "Fetching the structure";
      if (v.engine === "CODE") return "Running the program";
      return v.attempts > 0 ? "Fixing a hiccup and drawing again" : "Drawing the video";
    case "READY":
      return "Ready";
    case "FAILED":
      return "Could not be made";
  }
}

/** 0..1 for the progress bar: planning, writing and drawing each take a share. */
export function fraction(v: Visualization): number {
  switch (v.status) {
    case "PLANNING":
      return 0.15;
    case "CODING":
      return 0.4;
    case "RENDERING":
      return Math.min(0.9, 0.65 + v.attempts * 0.1);
    default:
      return 1;
  }
}

export const videoLength = (seconds: number | null) => {
  if (seconds == null) return "";
  const s = Math.round(seconds);
  return `${Math.floor(s / 60)}:${String(s % 60).padStart(2, "0")}`;
};

/** Which beat is playing at time t, from the narration timings. */
export function beatAt(timing: { start: number; seconds: number }[], t: number): number {
  let index = 0;
  timing.forEach((span, i) => {
    if (t >= span.start) index = i;
  });
  return index;
}

/** "3-5" or "4" as an inclusive line range; null when empty or not lines. */
export function lineRange(focus: string | undefined): [number, number] | null {
  const m = /^\s*(\d+)\s*(?:-\s*(\d+))?\s*$/.exec(focus ?? "");
  if (!m) return null;
  const a = Number(m[1]);
  const b = m[2] ? Number(m[2]) : a;
  return [Math.min(a, b), Math.max(a, b)];
}

/**
 * Which trace steps each beat steps through: in order, those on the beat's lines, never going back in time (so a
 * loop explained in beat 2 continues from where beat 1 stopped). A beat without lines gets none.
 */
export function stepsPerBeat(lines: ([number, number] | null)[], steps: { line: number }[]): number[][] {
  let cursor = 0;
  return lines.map((range) => {
    if (!range) return [];
    const picked: number[] = [];
    steps.forEach((step, i) => {
      if (i >= cursor && step.line >= range[0] && step.line <= range[1]) picked.push(i);
    });
    const lastPicked = picked[picked.length - 1];
    if (lastPicked !== undefined) cursor = lastPicked + 1;
    return picked;
  });
}

/** A protein focus: "A:58-63" (chain and residues), "ligand:HEM", "A" (a chain); null when empty. */
export function proteinFocus(focus: string | undefined): { chain?: string; resi?: string[]; resn?: string } | null {
  const f = (focus ?? "").trim();
  if (!f) return null;
  const ligand = /^ligand\s*:\s*([A-Za-z0-9]{1,3})$/i.exec(f);
  if (ligand?.[1]) return { resn: ligand[1].toUpperCase() };
  const m = /^([A-Za-z])(?:\s*:\s*([\d,\s-]+))?$/.exec(f);
  if (!m?.[1]) return null;
  const resi = m[2]?.split(",").map((r) => r.replace(/\s+/g, "")).filter(Boolean);
  return { chain: m[1].toUpperCase(), ...(resi?.length ? { resi } : {}) };
}
