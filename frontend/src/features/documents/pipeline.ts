import type { DocumentDto } from "./types";

export const PIPELINE_STEPS = ["Uploaded", "Reading text", "Building your course", "Ready"] as const;

export interface PipelineState {
  /** Index of the step currently running (or failed), 0..3. 3 + done = ready. */
  current: number;
  failed: boolean;
  done: boolean;
  label: string;
}

/** Maps a document's status to the four-step pipeline (endowed progress: "Uploaded" is always done). */
export function pipelineState(doc: Pick<DocumentDto, "status" | "textPages">): PipelineState {
  switch (doc.status) {
    case "PENDING":
      return { current: 1, failed: false, done: false, label: "Queued" };
    case "EXTRACTING":
      return { current: 1, failed: false, done: false, label: "Reading text" };
    case "ANALYZING":
      return { current: 2, failed: false, done: false, label: "Building your course" };
    case "READY":
      return { current: 3, failed: false, done: true, label: "Ready" };
    case "FAILED":
      return { current: doc.textPages != null ? 2 : 1, failed: true, done: false, label: "Needs another try" };
  }
}
