import { useEffect, useRef } from "react";

/** Calls `callback` every `intervalMs` while `active` is true. */
export function usePolling(callback: () => void, intervalMs: number, active: boolean): void {
  const saved = useRef(callback);
  useEffect(() => {
    saved.current = callback;
  }, [callback]);
  useEffect(() => {
    if (!active) return;
    const id = window.setInterval(() => saved.current(), intervalMs);
    return () => window.clearInterval(id);
  }, [intervalMs, active]);
}
