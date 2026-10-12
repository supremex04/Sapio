import type { NextStep } from "./types";

/** Where the "Next best step" card leads. */
export function nextStepHref(step: NextStep): string {
  switch (step.kind) {
    case "REVIEW":
      return step.documentId ? `/recall?documentId=${step.documentId}` : "/recall";
    case "CONTINUE_TOPIC":
    case "START_TOPIC":
      return step.documentId && step.topicId ? `/documents/${step.documentId}/learn/${step.topicId}` : step.documentId ? `/documents/${step.documentId}/learn` : "/library";
    case "TAKE_TEST":
      return step.documentId ? `/documents/${step.documentId}/test` : "/library";
    case "UPLOAD":
      return "/library";
  }
}

export function nextStepCta(step: NextStep): string {
  return { REVIEW: "Start recall", CONTINUE_TOPIC: "Continue lesson", START_TOPIC: "Start lesson", TAKE_TEST: "Test myself", UPLOAD: "Upload material" }[step.kind];
}

/** Recall cards take roughly 20 seconds each; shown as "~2 min" to lower the perceived effort. */
export function reviewMinutes(count: number): number {
  return Math.max(1, Math.ceil((count * 20) / 60));
}

/** Minutes a test takes, roughly (~0.6 min per question). */
export function testMinutes(count: number): number {
  return Math.max(2, Math.round(count * 0.6));
}
