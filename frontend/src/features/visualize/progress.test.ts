import { describe, expect, it } from "vitest";
import { videoLength, fraction, stage } from "./progress";
import type { Visualization } from "./types";

const base: Visualization = {
  id: "v",
  request: "a^2 + b^2 = c^2",
  documentId: null,
  status: "PLANNING",
  title: null,
  kind: null,
  beats: [],
  code: null,
  attempts: 0,
  error: null,
  durationSeconds: null,
  narrated: false,
  videoUrl: null,
  thumbnailUrl: null,
  createdAt: "2026-10-16T10:00:00Z",
  completedAt: null,
};

describe("visualization progress", () => {
  it("moves forward through the stages", () => {
    const order = (["PLANNING", "CODING", "RENDERING", "READY"] as const).map((status) => fraction({ ...base, status }));
    expect(order).toEqual([...order].sort((a, b) => a - b));
  });

  it("says when a failed render is being repaired", () => {
    expect(stage({ ...base, status: "RENDERING" })).toBe("Drawing the video");
    expect(stage({ ...base, status: "RENDERING", attempts: 1 })).toContain("Fixing");
  });

  it("formats video length as m:ss", () => {
    expect(videoLength(4.4)).toBe("0:04");
    expect(videoLength(75)).toBe("1:15");
    expect(videoLength(null)).toBe("");
  });
});
