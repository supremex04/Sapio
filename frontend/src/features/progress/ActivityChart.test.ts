import { describe, expect, it } from "vitest";
import { level, summarize } from "./ActivityChart";
import type { DailyActivity } from "./types";

const day = (date: string, minutes: number): DailyActivity => ({
  date,
  activeSeconds: minutes * 60,
  logins: 0,
  lessonsCompleted: 0,
  questionsAnswered: 0,
  correctAnswers: 0,
  xpEarned: 0,
});

describe("activity intensity", () => {
  it("is relative to the daily goal: full colour means the goal was met", () => {
    expect(level(0, 10)).toBe(0);
    expect(level(2 * 60, 10)).toBe(1);
    expect(level(5 * 60, 10)).toBe(2);
    expect(level(10 * 60, 10)).toBe(3);
    expect(level(15 * 60, 10)).toBe(4);
  });

  it("falls back to a 15 minute reference without a goal", () => {
    expect(level(14 * 60, null)).toBe(2);
    expect(level(15 * 60, null)).toBe(3);
  });
});

describe("range summary", () => {
  it("counts active days, total time, goal days and the longest run", () => {
    const days = [day("2026-10-01", 5), day("2026-10-02", 12), day("2026-10-03", 0), day("2026-10-04", 3), day("2026-10-05", 20), day("2026-10-06", 1)];
    expect(summarize(days, 10)).toEqual({ activeDays: 5, totalSeconds: 41 * 60, goalDays: 2, longest: 3 });
    expect(summarize(days, null).goalDays).toBeNull();
  });
});
