import { describe, expect, it } from "vitest";
import { buildLessonSteps, initialRevealCount, masteryLabel, nextTopicAfter, nodeState } from "./path";
import type { AnswerResult, Lesson, LessonCheck, Topic } from "./types";

const topic = (over: Partial<Topic>): Topic => ({
  id: "t",
  position: 1,
  title: "T",
  summary: "",
  keyConcepts: [],
  difficulty: "CORE",
  status: "NOT_STARTED",
  completedAt: null,
  mastery: null,
  recommended: false,
  ...over,
});

const answer: AnswerResult = {
  questionId: "q",
  verdict: "CORRECT",
  score: 1,
  selectedOptionIndex: 0,
  answerText: null,
  correctOptionIndex: 0,
  referenceAnswer: null,
  explanation: "",
  feedback: "",
  confidence: null,
  nextReviewAt: null,
};

const check = (id: string, afterSection: number, lastAnswer: AnswerResult | null = null): LessonCheck => ({
  id,
  afterSection,
  type: "MULTIPLE_CHOICE",
  prompt: id,
  options: ["a", "b"],
  lastAnswer,
});

const lesson = (checks: LessonCheck[]): Lesson => ({
  topicId: "t",
  title: "T",
  sections: [1, 0].map((index) => ({ index, kind: "CONCEPT", heading: `s${index}`, body: "" })),
  keyTakeaways: [],
  checks,
  status: "IN_PROGRESS",
});

describe("nodeState", () => {
  it("prioritises the recommendation over status", () => {
    expect(nodeState(topic({ recommended: true, status: "COMPLETED" }))).toBe("recommended");
    expect(nodeState(topic({ status: "COMPLETED" }))).toBe("completed");
    expect(nodeState(topic({ status: "IN_PROGRESS" }))).toBe("in-progress");
    expect(nodeState(topic({}))).toBe("upcoming");
  });
});

describe("nextTopicAfter", () => {
  it("skips completed topics", () => {
    const topics = [topic({ id: "a", position: 1 }), topic({ id: "b", position: 2, status: "COMPLETED" }), topic({ id: "c", position: 3 })];
    expect(nextTopicAfter(topics, "a")?.id).toBe("c");
    expect(nextTopicAfter(topics, "c")).toBeUndefined();
  });
});

describe("buildLessonSteps", () => {
  it("puts warm-ups first, interleaves checks, appends out-of-range checks, ends with finish", () => {
    const steps = buildLessonSteps(lesson([check("explain", 5), check("warm", -1), check("c0", 0)]));
    expect(steps.map((s) => (s.kind === "section" ? s.section.heading : s.kind === "finish" ? "finish" : s.check.id))).toEqual([
      "warm",
      "s0",
      "c0",
      "s1",
      "explain",
      "finish",
    ]);
    expect(steps[0]?.kind).toBe("warmup");
  });
});

describe("initialRevealCount", () => {
  it("resumes after the last answered check", () => {
    const steps = buildLessonSteps(lesson([check("warm", -1, answer), check("c0", 0, answer), check("c1", 1)]));
    expect(initialRevealCount(steps, false)).toBe(4); // warm, s0, c0 answered → reveal s1 too
    expect(initialRevealCount(buildLessonSteps(lesson([])), false)).toBe(1);
    expect(initialRevealCount(steps, true)).toBe(steps.length);
  });
});

describe("masteryLabel", () => {
  it("buckets mastery", () => {
    expect(masteryLabel(null)).toMatch(/Not practised/);
    expect(masteryLabel(0.9)).toBe("Mastered");
    expect(masteryLabel(0.6)).toBe("Getting there");
    expect(masteryLabel(0.2)).toBe("Needs practice");
  });
});
