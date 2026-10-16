import { describe, expect, it } from "vitest";
import { beatAt, fraction, lineRange, proteinFocus, stage, stepsPerBeat, videoLength } from "./progress";
import type { Visualization } from "./types";

const base: Visualization = {
  id: "v",
  request: "a^2 + b^2 = c^2",
  documentId: null,
  status: "PLANNING",
  engineChoice: "AUTO",
  engine: "AUTO",
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
  audioUrl: null,
  structureUrl: null,
  scene: null,
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

describe("narrated scenes", () => {
  it("finds the beat playing at a time", () => {
    const timing = [
      { start: 0, seconds: 2 },
      { start: 2, seconds: 3 },
    ];
    expect(beatAt(timing, 0.5)).toBe(0);
    expect(beatAt(timing, 2)).toBe(1);
    expect(beatAt(timing, 99)).toBe(1);
  });

  it("gives each code beat the steps on its lines, forward in time only", () => {
    const steps = [1, 2, 3, 2, 3, 4].map((line) => ({ line }));
    expect(stepsPerBeat([lineRange("1"), lineRange("2-3"), lineRange(""), lineRange("2-4")], steps)).toEqual([[0], [1, 2, 3, 4], [], [5]]);
  });

  it("reads protein focus", () => {
    expect(proteinFocus("A:58-63")).toEqual({ chain: "A", resi: ["58-63"] });
    expect(proteinFocus("ligand:hem")).toEqual({ resn: "HEM" });
    expect(proteinFocus("b")).toEqual({ chain: "B" });
    expect(proteinFocus("")).toBeNull();
    expect(proteinFocus("the active site")).toBeNull();
  });
});
