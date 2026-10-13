import type { ReactNode } from "react";
import { cx } from "./cx";

/** Sapi's voice. The tail points left (towards Sapi) or up. */
export function SpeechBubble({ children, tail = "left", className }: { children: ReactNode; tail?: "left" | "up"; className?: string }) {
  return <div className={cx("bubble", tail === "up" && "bubble-up", className)}>{children}</div>;
}
