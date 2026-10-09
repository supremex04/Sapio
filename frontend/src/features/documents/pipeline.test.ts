import { describe, expect, it } from "vitest";
import { pipelineState } from "./pipeline";

describe("pipelineState", () => {
  it("maps statuses to steps", () => {
    expect(pipelineState({ status: "PENDING", textPages: null }).current).toBe(1);
    expect(pipelineState({ status: "ANALYZING", textPages: 3 }).current).toBe(2);
    expect(pipelineState({ status: "READY", textPages: 3 })).toMatchObject({ current: 3, done: true });
  });

  it("locates failures at the stage that failed", () => {
    expect(pipelineState({ status: "FAILED", textPages: null })).toMatchObject({ current: 1, failed: true });
    expect(pipelineState({ status: "FAILED", textPages: 4 })).toMatchObject({ current: 2, failed: true });
  });
});
