import type { Lesson, LessonCheck, LessonSection, Topic } from "./types";

export type NodeState = "completed" | "recommended" | "in-progress" | "upcoming";

/** Visual state of a topic node on the learning path. */
export function nodeState(topic: Topic): NodeState {
  if (topic.status === "COMPLETED" && !topic.recommended) return "completed";
  if (topic.recommended) return "recommended";
  if (topic.status === "IN_PROGRESS") return "in-progress";
  return "upcoming";
}

/**
 * Horizontal offset of the n-th path node, in [-1, 1]: a smooth sine so the path winds left and
 * right (CSS multiplies it by the amplitude for the screen size). Starts centred.
 */
export function pathWave(index: number): number {
  return Math.round(Math.sin(index * 0.9) * 1000) / 1000;
}

/** Label of the bouncing bubble above the recommended node. */
export function startLabel(topic: Pick<Topic, "status">): string {
  if (topic.status === "IN_PROGRESS") return "Continue";
  if (topic.status === "COMPLETED") return "Strengthen";
  return "Start";
}

/** The recommended topic, falling back to the first unfinished one (server normally marks it). */
export function recommendedTopic(topics: Topic[]): Topic | undefined {
  return topics.find((t) => t.recommended) ?? topics.find((t) => t.status !== "COMPLETED");
}

export function nextTopicAfter(topics: Topic[], topicId: string): Topic | undefined {
  const sorted = [...topics].sort((a, b) => a.position - b.position);
  const i = sorted.findIndex((t) => t.id === topicId);
  return i >= 0 ? sorted.slice(i + 1).find((t) => t.status !== "COMPLETED") ?? sorted[i + 1] : undefined;
}

export type LessonStep =
  | { kind: "warmup"; check: LessonCheck }
  | { kind: "section"; section: LessonSection }
  | { kind: "check"; check: LessonCheck }
  | { kind: "finish" };

/**
 * Orders a lesson into one-at-a-time steps: warm-up checks (afterSection −1) first, then each
 * section followed by its checks, checks pointing past the last section at the end, then finish.
 */
export function buildLessonSteps(lesson: Lesson): LessonStep[] {
  const sections = [...lesson.sections].sort((a, b) => a.index - b.index);
  const lastIndex = sections.at(-1)?.index ?? -1;
  const steps: LessonStep[] = lesson.checks.filter((c) => c.afterSection < 0).map((check) => ({ kind: "warmup" as const, check }));
  for (const section of sections) {
    steps.push({ kind: "section", section });
    for (const check of lesson.checks) {
      if (check.afterSection === section.index) steps.push({ kind: "check", check });
    }
  }
  for (const check of lesson.checks) {
    if (check.afterSection > lastIndex) steps.push({ kind: "check", check });
  }
  steps.push({ kind: "finish" });
  return steps;
}

/** How many steps to reveal when (re)opening a lesson: through the last answered check. */
export function initialRevealCount(steps: LessonStep[], completed: boolean): number {
  if (completed) return steps.length;
  let lastAnswered = -1;
  steps.forEach((s, i) => {
    if ((s.kind === "check" || s.kind === "warmup") && s.check.lastAnswer) lastAnswered = i;
  });
  return Math.max(1, lastAnswered + 2);
}

export function masteryLabel(mastery: number | null): string {
  if (mastery == null) return "Not practised yet";
  if (mastery >= 0.8) return "Mastered";
  if (mastery >= 0.5) return "Getting there";
  return "Needs practice";
}
