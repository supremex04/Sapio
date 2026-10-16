import type { Visualization } from "./types";

/** What the learner reads while a visualization is being made. */
export function stage(v: Visualization): string {
  switch (v.status) {
    case "PLANNING":
      return "Planning the scenes";
    case "CODING":
      return "Writing the animation";
    case "RENDERING":
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
