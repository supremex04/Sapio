import { useCallback, useEffect, useState } from "react";
import { classroomApi } from "./api";
import { gridRange } from "./calendar";
import type { CalendarItem } from "./types";

/** The calendar items for a month's grid, refetched when the month changes (or on reload()). */
export function useCalendar(year: number, month: number) {
  const [items, setItems] = useState<CalendarItem[] | null>(null);
  const [error, setError] = useState(false);
  const load = useCallback(async () => {
    const { from, to } = gridRange(year, month);
    try {
      setItems(await classroomApi.calendar(from, to));
      setError(false);
    } catch {
      setError(true);
    }
  }, [year, month]);
  useEffect(() => {
    void load();
  }, [load]);
  return { items, error, reload: load };
}
