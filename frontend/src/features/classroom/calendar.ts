import type { CalendarItem } from "./types";

/** The 6 weeks shown for a month (Monday first), as local dates at midnight. */
export function monthGrid(year: number, month: number): Date[] {
  const first = new Date(year, month, 1);
  const lead = (first.getDay() + 6) % 7; // Monday = 0
  return Array.from({ length: 42 }, (_, i) => new Date(year, month, 1 - lead + i));
}

export const sameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();

const startOfDay = (d: Date) => new Date(d.getFullYear(), d.getMonth(), d.getDate());

/** Items happening on a local day: starting that day, or spanning it (multi-day events). */
export function itemsOn(items: CalendarItem[], day: Date): CalendarItem[] {
  const from = startOfDay(day).getTime();
  const to = from + 24 * 60 * 60 * 1000;
  return items.filter((it) => {
    const start = new Date(it.startsAt).getTime();
    const end = it.endsAt ? new Date(it.endsAt).getTime() : start;
    return start < to && end >= from;
  });
}

/** Next items from now (or still running), soonest first. */
export function upcoming(items: CalendarItem[], now: Date, limit: number): CalendarItem[] {
  const today = startOfDay(now).getTime();
  return items
    .filter((it) => new Date(it.endsAt ?? it.startsAt).getTime() >= today)
    .sort((a, b) => new Date(a.startsAt).getTime() - new Date(b.startsAt).getTime())
    .slice(0, limit);
}

/** The range to fetch for a month's grid. */
export function gridRange(year: number, month: number): { from: Date; to: Date } {
  const grid = monthGrid(year, month);
  const last = grid[grid.length - 1]!;
  return { from: grid[0]!, to: new Date(last.getFullYear(), last.getMonth(), last.getDate() + 1) };
}

const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: "short", month: "short", day: "numeric" });
const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });

/** "Fri, Oct 18 · 2:00 PM" or "Fri, Oct 18" for all-day items. */
export function whenLabel(it: CalendarItem): string {
  const start = new Date(it.startsAt);
  if (it.allDay) return dayFormat.format(start);
  return `${dayFormat.format(start)} · ${timeFormat.format(start)}`;
}
