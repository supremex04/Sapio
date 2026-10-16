import { describe, expect, it } from "vitest";
import { itemsOn, monthGrid, upcoming } from "./calendar";
import type { CalendarItem } from "./types";

const item = (title: string, startsAt: Date, endsAt?: Date): CalendarItem => ({
  id: title,
  kind: "EVENT",
  title,
  description: null,
  startsAt: startsAt.toISOString(),
  endsAt: endsAt ? endsAt.toISOString() : null,
  allDay: false,
  teacherName: "T",
  link: null,
});

describe("calendar", () => {
  it("lays a month out in six Monday-first weeks", () => {
    const grid = monthGrid(2026, 9); // October 2026 starts on a Thursday
    expect(grid).toHaveLength(42);
    expect(grid[0]!.getDay()).toBe(1);
    expect(grid[3]!.getDate()).toBe(1);
  });

  it("puts multi-day events on every day they span", () => {
    const trip = item("trip", new Date(2026, 9, 20, 9), new Date(2026, 9, 22, 17));
    expect(itemsOn([trip], new Date(2026, 9, 21))).toHaveLength(1);
    expect(itemsOn([trip], new Date(2026, 9, 23))).toHaveLength(0);
  });

  it("lists what is coming, soonest first", () => {
    const now = new Date(2026, 9, 16, 12);
    const items = [item("later", new Date(2026, 9, 30)), item("past", new Date(2026, 9, 1)), item("soon", new Date(2026, 9, 17))];
    expect(upcoming(items, now, 5).map((i) => i.title)).toEqual(["soon", "later"]);
  });
});
