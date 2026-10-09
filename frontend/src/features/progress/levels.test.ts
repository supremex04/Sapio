import { describe, expect, it } from "vitest";
import { goalProgress, levelProgress, streakMessage, xpForLevel } from "./levels";

describe("levels", () => {
  it("follows the spec thresholds", () => {
    expect([1, 2, 3, 4, 5].map(xpForLevel)).toEqual([0, 100, 300, 600, 1000]);
  });

  it("computes progress within a level", () => {
    expect(levelProgress({ total: 420, levelStartXp: 300, nextLevelXp: 600 })).toBeCloseTo(0.4);
    expect(levelProgress({ total: 300, levelStartXp: 300, nextLevelXp: 600 })).toBe(0);
    expect(levelProgress({ total: 10, levelStartXp: 0, nextLevelXp: 0 })).toBe(1);
  });

  it("caps goal progress at 100%", () => {
    expect(goalProgress({ dailyMinutes: 10, todayMinutes: 5 })).toBe(0.5);
    expect(goalProgress({ dailyMinutes: 10, todayMinutes: 25 })).toBe(1);
  });

  it("never shames an inactive day", () => {
    expect(streakMessage({ current: 4, activeToday: false })).not.toMatch(/lost|broke|miss/i);
    expect(streakMessage({ current: 0, activeToday: false })).toMatch(/start/);
  });
});
