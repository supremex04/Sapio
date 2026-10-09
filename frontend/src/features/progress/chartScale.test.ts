import { describe, expect, it } from "vitest";
import { niceTicks } from "./chartScale";

describe("niceTicks", () => {
  it("produces clean, covering ticks", () => {
    expect(niceTicks(0)).toEqual([0, 2, 4, 6]);
    expect(niceTicks(10)).toEqual([0, 5, 10]);
    expect(niceTicks(37)).toEqual([0, 10, 20, 30, 40]);
    expect(niceTicks(130).at(-1)).toBeGreaterThanOrEqual(130);
  });
});
