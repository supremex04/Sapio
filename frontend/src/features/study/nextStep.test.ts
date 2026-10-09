import { describe, expect, it } from "vitest";
import { nextStepHref, reviewMinutes } from "./nextStep";
import type { NextStep } from "./types";

const step = (over: Partial<NextStep>): NextStep => ({ kind: "UPLOAD", documentId: null, topicId: null, title: "", reason: "", estimatedMinutes: null, ...over });

describe("nextStepHref", () => {
  it("routes each kind", () => {
    expect(nextStepHref(step({ kind: "REVIEW" }))).toBe("/review");
    expect(nextStepHref(step({ kind: "CONTINUE_TOPIC", documentId: "d", topicId: "t" }))).toBe("/documents/d/learn/t");
    expect(nextStepHref(step({ kind: "TAKE_TEST", documentId: "d" }))).toBe("/documents/d/test");
    expect(nextStepHref(step({ kind: "UPLOAD" }))).toBe("/library");
  });
});

describe("reviewMinutes", () => {
  it("rounds up with a 1 minute floor", () => {
    expect(reviewMinutes(1)).toBe(1);
    expect(reviewMinutes(6)).toBe(2);
    expect(reviewMinutes(10)).toBe(4);
  });
});
