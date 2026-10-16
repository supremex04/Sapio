/** Helpers for the date-time picker: local dates and minutes-of-day to instants and back. */

export const MINUTES_IN_DAY = 24 * 60;

/** A local date (y, m, d) at minutes past midnight, as an ISO instant. */
export function combine(day: Date, minutes: number): string {
  const d = new Date(day.getFullYear(), day.getMonth(), day.getDate(), Math.floor(minutes / 60), minutes % 60);
  return d.toISOString();
}

/** Minutes past local midnight of an instant. */
export function minutesOf(iso: string): number {
  const d = new Date(iso);
  return d.getHours() * 60 + d.getMinutes();
}

/** Every quarter hour of a day, plus 23:59 ("end of day"). */
export function timeOptions(): number[] {
  const quarters = Array.from({ length: MINUTES_IN_DAY / 15 }, (_, i) => i * 15);
  return [...quarters, MINUTES_IN_DAY - 1];
}

const timeFormat = new Intl.DateTimeFormat(undefined, { hour: "numeric", minute: "2-digit" });
const dayFormat = new Intl.DateTimeFormat(undefined, { weekday: "short", month: "short", day: "numeric" });
const dayYearFormat = new Intl.DateTimeFormat(undefined, { weekday: "short", month: "short", day: "numeric", year: "numeric" });

export function timeLabel(minutes: number): string {
  return timeFormat.format(new Date(2000, 0, 1, Math.floor(minutes / 60), minutes % 60));
}

/** "Fri, Oct 18 · 11:59 PM" (the year only when it is not this one). */
export function valueLabel(iso: string, withTime: boolean, now: Date = new Date()): string {
  const d = new Date(iso);
  const day = (d.getFullYear() === now.getFullYear() ? dayFormat : dayYearFormat).format(d);
  return withTime ? `${day} · ${timeFormat.format(d)}` : day;
}

/** The 6 weeks shown for a month (Monday first), as local dates at midnight. */
export function monthGrid(year: number, month: number): Date[] {
  const first = new Date(year, month, 1);
  const lead = (first.getDay() + 6) % 7; // Monday = 0
  return Array.from({ length: 42 }, (_, i) => new Date(year, month, 1 - lead + i));
}

export const sameDay = (a: Date, b: Date) =>
  a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
