import type { IconName } from "../../shared/illustrations/Emoji";
import type { AnswerResult, Confidence, Verdict } from "./types";

export const CONFIDENCE_OPTIONS: { value: Confidence; label: string; emoji: IconName }[] = [
  { value: "LOW", label: "Guessing", emoji: "thinking-face" },
  { value: "MEDIUM", label: "Fairly sure", emoji: "slightly-smiling-face" },
  { value: "HIGH", label: "Certain", emoji: "smiling-face-with-sunglasses" },
];

export type Tone = "success" | "warning";

/** Growth-mindset verdict copy. Mistakes get a soft tint and encouraging words (learning-science.md §3). */
export function verdictCopy(verdict: Verdict, context: "warmup" | "normal"): { title: string; tone: Tone } {
  if (verdict === "CORRECT") return { title: context === "warmup" ? "Good instinct!" : "Correct", tone: "success" };
  if (verdict === "PARTIAL") return { title: "Partly there", tone: "warning" };
  return { title: context === "warmup" ? "Good guess, now let's learn it" : "Not quite yet", tone: "warning" };
}

/** Hypercorrection: a confident error, once corrected, is remembered especially well. */
export function isConfidentMiss(result: Pick<AnswerResult, "verdict" | "confidence">): boolean {
  return result.verdict === "INCORRECT" && (result.confidence === "HIGH" || result.confidence === "MEDIUM");
}

export function optionLetter(index: number): string {
  return String.fromCharCode(65 + index);
}
