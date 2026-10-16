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

/** Most steps a beat walks through; longer runs are sampled evenly (first and last kept), to stay readable. */
export const MAX_STEPS_PER_BEAT = 12;

/**
 * Which trace steps each beat walks through: those on the beat's lines, in order, as one stretch of the run. A
 * stretch ends at a long jump (a line that runs at the start and again at the very end, like a final print, is not
 * one moment). Beats never start before the previous beat started, so the story moves forward, but a beat may
 * revisit the same passes ("now the inner loop"). A beat without lines gets none.
 */
export function stepsPerBeat(lines: ([number, number] | null)[], steps: { line: number }[]): number[][] {
  const maxGap = Math.max(30, Math.round(steps.length * 0.35));
  let cursor = 0;
  return lines.map((range) => {
    if (!range) return [];
    const picked: number[] = [];
    for (let i = cursor; i < steps.length; i++) {
      const step = steps[i];
      if (!step || step.line < range[0] || step.line > range[1]) continue;
      const previous = picked[picked.length - 1];
      if (previous !== undefined && i - previous > maxGap) break;
      picked.push(i);
    }
    const first = picked[0];
    if (first !== undefined) cursor = first + 1;
    return sample(picked, MAX_STEPS_PER_BEAT);
  });
}

function sample(items: number[], most: number): number[] {
  if (items.length <= most) return items;
  return Array.from({ length: most }, (_, k) => items[Math.round((k * (items.length - 1)) / (most - 1))] ?? items[0] ?? 0);
}

/** A protein focus: "A:58-63" (chain and residues), "ligand:HEM", "A" (a chain); null when empty. */
export function proteinFocus(focus: string | undefined): { chain?: string; resi?: string[]; resn?: string } | null {
  const f = (focus ?? "").trim();
  if (!f) return null;
  const ligand = /^ligand\s*:\s*([A-Za-z0-9]{1,3})$/i.exec(f);
  if (ligand?.[1]) return { resn: ligand[1].toUpperCase() };
  const m = /^([A-Za-z0-9])(?:\s*:\s*(?:[A-Za-z]{3}\s*)?([\d,\s-]+?)(?:\s*:?\s*[A-Za-z]{3})?)?$/.exec(f);
  if (!m?.[1]) return null;
  const resi = m[2]?.split(",").map((r) => r.replace(/\s+/g, "")).filter(Boolean);
  return { chain: m[1].toUpperCase(), ...(resi?.length ? { resi } : {}) };
}
