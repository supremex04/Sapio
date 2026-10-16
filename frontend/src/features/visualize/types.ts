export type VisualizationStatus = "PLANNING" | "CODING" | "RENDERING" | "READY" | "FAILED";

/** One scene of the storyboard: what appears on screen, and the line of narration under it. */
export interface Beat {
  shows: string;
  says: string;
}

export interface Visualization {
  id: string;
  request: string;
  documentId: string | null;
  status: VisualizationStatus;
  title: string | null;
  kind: string | null;
  beats: Beat[];
  code: string | null;
  attempts: number;
  error: string | null;
  durationSeconds: number | null;
  /** Signed, expiring; null until READY. */
  videoUrl: string | null;
  thumbnailUrl: string | null;
  createdAt: string;
  completedAt: string | null;
}

export const isWorking = (v: Visualization) => v.status === "PLANNING" || v.status === "CODING" || v.status === "RENDERING";
