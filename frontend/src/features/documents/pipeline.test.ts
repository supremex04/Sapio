import { describe, expect, it } from "vitest";
import { pipelineState, stageProgress, workStage } from "./pipeline";
import type { DocumentStatus } from "./types";

const doc = (status: DocumentStatus, textPages: number | null = null, retryAt: string | null = null) => ({
  status,
  textPages,
  retryAt,
});

describe("pipelineState", () => {
  it("maps statuses to steps", () => {
    expect(pipelineState(doc("PENDING")).current).toBe(1);
    expect(pipelineState(doc("INDEXING", 3)).current).toBe(2);
    expect(pipelineState(doc("ANALYZING", 3)).current).toBe(2);
    expect(pipelineState(doc("READY", 3))).toMatchObject({ current: 3, done: true });
  });

  it("locates failures at the stage that failed", () => {
    expect(pipelineState(doc("FAILED"))).toMatchObject({ current: 1, failed: true });
    expect(pipelineState(doc("FAILED", 4))).toMatchObject({ current: 2, failed: true });
  });

  it("treats a failure with a scheduled retry as paused, not failed", () => {
    expect(pipelineState(doc("FAILED", 4, "2026-10-10T12:00:00Z"))).toMatchObject({ paused: true, failed: false });
  });

  it("never crashes on a status it doesn't know", () => {
    expect(pipelineState(doc("SOMETHING_NEW" as DocumentStatus))).toMatchObject({ failed: false, done: false });
  });
});

describe("workStage", () => {
  it("tells what Dia is doing, with a sleepy pause", () => {
    expect(workStage(doc("INDEXING", 3)).lines.length).toBeGreaterThan(1);
    expect(workStage(doc("FAILED", 3, "2026-10-10T12:00:00Z")).mood).toBe("sleepy");
  });

  it("creeps through the stage band without reaching its end", () => {
    expect(stageProgress([0.4, 0.8], 0)).toBe(0.4);
    expect(stageProgress([0.4, 0.8], 600)).toBeLessThan(0.8);
  });
});
