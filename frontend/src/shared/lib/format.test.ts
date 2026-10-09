import { describe, expect, it } from "vitest";
import { firstName, formatDuration, formatPercent, greeting } from "./format";

describe("format", () => {
  it("formats durations compactly", () => {
    expect(formatDuration(0)).toBe("0m");
    expect(formatDuration(45)).toBe("45s");
    expect(formatDuration(720)).toBe("12m");
    expect(formatDuration(3900)).toBe("1h 5m");
    expect(formatDuration(7200)).toBe("2h");
  });

  it("formats percentages and handles missing values", () => {
    expect(formatPercent(0.725)).toBe("73%");
    expect(formatPercent(null)).toBe("—");
  });

  it("greets by time of day", () => {
    expect(greeting(new Date(2026, 0, 1, 9))).toBe("Good morning");
    expect(greeting(new Date(2026, 0, 1, 15))).toBe("Good afternoon");
    expect(greeting(new Date(2026, 0, 1, 21))).toBe("Good evening");
  });

  it("extracts first names", () => {
    expect(firstName("  Asha Devi Sharma ")).toBe("Asha");
    expect(firstName(undefined)).toBe("");
  });
});
