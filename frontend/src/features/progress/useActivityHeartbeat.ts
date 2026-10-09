import { useEffect } from "react";
import { browserTimeZone } from "../../shared/lib/timezone";
import { progressApi } from "./api";

const INTERVAL_MS = 30_000;
const IDLE_AFTER_MS = 60_000;

/**
 * Reports active learning time: every 30 s, if the tab is visible and the user interacted in the
 * last 60 s, send a heartbeat. The server credits at most 30 s per heartbeat (progress.md).
 */
export function useActivityHeartbeat(): void {
  useEffect(() => {
    let lastInteraction = Date.now();
    let lastMove = 0;
    const markActive = () => {
      lastInteraction = Date.now();
    };
    const onMove = () => {
      const now = Date.now();
      if (now - lastMove > 5000) {
        lastMove = now;
        lastInteraction = now;
      }
    };
    const events = ["pointerdown", "keydown", "scroll", "touchstart"] as const;
    events.forEach((e) => window.addEventListener(e, markActive, { passive: true }));
    window.addEventListener("mousemove", onMove, { passive: true });

    const timeZone = browserTimeZone();
    const id = window.setInterval(() => {
      if (document.visibilityState !== "visible") return;
      if (Date.now() - lastInteraction > IDLE_AFTER_MS) return;
      progressApi.heartbeat(timeZone).catch(() => undefined);
    }, INTERVAL_MS);

    return () => {
      window.clearInterval(id);
      events.forEach((e) => window.removeEventListener(e, markActive));
      window.removeEventListener("mousemove", onMove);
    };
  }, []);
}
