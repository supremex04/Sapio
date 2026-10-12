import type { IconName } from "../../shared/illustrations/Emoji";
import type { DiaMood } from "../../shared/illustrations/Dia";
import type { DocumentDto } from "./types";

export const PIPELINE_STEPS = ["Uploaded", "Reading text", "Building your course", "Ready"] as const;

type PipelineDoc = Pick<DocumentDto, "status" | "textPages" | "retryAt">;

export interface PipelineState {
  /** Index of the step currently running (or failed), 0..3. 3 + done = ready. */
  current: number;
  failed: boolean;
  /** Failed for a transient reason (AI busy); continues automatically at `retryAt`. */
  paused: boolean;
  done: boolean;
  label: string;
}

/** Maps a document's status to the four-step pipeline (endowed progress: "Uploaded" is always done). */
export function pipelineState(doc: PipelineDoc): PipelineState {
  const step = (current: number, label: string): PipelineState => ({ current, failed: false, paused: false, done: false, label });
  switch (doc.status) {
    case "PENDING":
      return step(1, "Queued");
    case "EXTRACTING":
      return step(1, "Reading text");
    case "INDEXING":
      return step(2, "Organising chapters");
    case "ANALYZING":
      return step(2, "Building your course");
    case "READY":
      return { current: 3, failed: false, paused: false, done: true, label: "Ready" };
    case "FAILED": {
      const current = doc.textPages != null ? 2 : 1;
      return doc.retryAt
        ? { current, failed: false, paused: true, done: false, label: "Taking a breather" }
        : { current, failed: true, paused: false, done: false, label: "Needs another try" };
    }
    default:
      // A stage this UI doesn't know yet: still "working", never a crash.
      return step(2, "Working on it");
  }
}

/** What Dia is visibly doing at each stage ("labor illusion": seeing the work makes waiting feel worthwhile). */
export interface WorkStage {
  emoji: IconName;
  mood: DiaMood;
  /** Concrete, rotating descriptions of the work in progress. */
  lines: string[];
  /** Share of the overall progress bar this stage covers [from, to]. */
  band: [number, number];
}

export function workStage(doc: PipelineDoc): WorkStage {
  const state = pipelineState(doc);
  if (state.paused) {
    return {
      emoji: "sleeping-face",
      mood: "sleepy",
      lines: [`The AI is busy, so I'm resting a moment. I'll pick up where I left off${until(doc.retryAt)}.`],
      band: doc.textPages != null ? [0.45, 0.45] : [0.12, 0.12],
    };
  }
  switch (doc.status) {
    case "PENDING":
      return { emoji: "hourglass-not-done", mood: "happy", lines: ["Waiting my turn…", "Sharpening my pencil…"], band: [0.04, 0.12] };
    case "EXTRACTING":
      return {
        emoji: "open-book",
        mood: "thinking",
        lines: ["Reading page by page…", "Turning scanned pages into text…", "Spotting headings and chapters…"],
        band: [0.12, 0.45],
      };
    case "INDEXING":
      return {
        emoji: "magnifying-glass-tilted-left",
        mood: "thinking",
        lines: [
          "Sorting chapters into a tree…",
          "Looking closely at every diagram…",
          "Linking figures to their sections…",
          "Picking out the key terms…",
        ],
        band: [0.45, 0.75],
      };
    default:
      return {
        emoji: "compass",
        mood: "encourage",
        lines: ["Drawing your learning path…", "Grouping ideas into bite-size topics…", "Ordering topics from the basics up…"],
        band: [0.75, 0.95],
      };
  }
}

function until(retryAt: string | null): string {
  if (!retryAt) return "";
  const at = new Date(retryAt);
  return Number.isNaN(at.getTime()) ? "" : ` around ${at.toLocaleTimeString([], { hour: "2-digit", minute: "2-digit" })}`;
}

/** Creeps through the stage's band (fast at first, never reaching its end): honest motion without fake precision. */
export function stageProgress([from, to]: [number, number], seconds: number): number {
  return from + (to - from) * (1 - Math.exp(-seconds / 25));
}
