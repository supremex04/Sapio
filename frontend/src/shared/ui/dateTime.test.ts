import { describe, expect, it } from "vitest";
import { combine, minutesOf, timeOptions, valueLabel } from "./dateTime";

describe("date-time helpers", () => {
  it("combines a local day and a time, and reads the time back", () => {
    const iso = combine(new Date(2026, 9, 18), 23 * 60 + 59);
    const d = new Date(iso);
    expect([d.getFullYear(), d.getMonth(), d.getDate(), d.getHours(), d.getMinutes()]).toEqual([2026, 9, 18, 23, 59]);
    expect(minutesOf(iso)).toBe(23 * 60 + 59);
  });

  it("offers quarter hours and the end of the day", () => {
    const options = timeOptions();
    expect(options[0]).toBe(0);
    expect(options).toContain(9 * 60 + 15);
    expect(options.at(-1)).toBe(23 * 60 + 59);
  });

  it("shows the year only for other years", () => {
    const now = new Date(2026, 0, 5);
    expect(valueLabel(combine(new Date(2026, 9, 18), 600), false, now)).not.toMatch(/2026/);
    expect(valueLabel(combine(new Date(2027, 9, 18), 600), false, now)).toMatch(/2027/);
  });
});
