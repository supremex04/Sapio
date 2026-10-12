import { describe, expect, it } from "vitest";
import { DEFAULT_TEXT_STEP, stepBy } from "./textScale";

describe("stepBy", () => {
  it("moves one step and stops at the ends", () => {
    expect(stepBy(25, 1)).toBe(50);
    expect(stepBy(25, -1)).toBe(0);
    expect(stepBy(0, -1)).toBe(0);
    expect(stepBy(100, 1)).toBe(100);
  });

  it("starts one step above the base size", () => {
    expect(DEFAULT_TEXT_STEP).toBe(25);
  });
});
