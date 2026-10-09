import confetti from "canvas-confetti";

const reducedMotion = () => window.matchMedia("(prefers-reduced-motion: reduce)").matches;

const COLORS = ["#5b4bdb", "#8b7cff", "#f59f00", "#ffb938", "#1a9a52"];

/** Short, tasteful burst for real achievements only (topic done, strong result, level up). */
export function celebrate(intensity: "small" | "big" = "small"): void {
  if (reducedMotion()) return;
  const base = { colors: COLORS, disableForReducedMotion: true, zIndex: 60 };
  if (intensity === "small") {
    void confetti({ ...base, particleCount: 70, spread: 70, origin: { y: 0.7 }, scalar: 0.9 });
    return;
  }
  void confetti({ ...base, particleCount: 90, angle: 60, spread: 65, origin: { x: 0, y: 0.75 } });
  void confetti({ ...base, particleCount: 90, angle: 120, spread: 65, origin: { x: 1, y: 0.75 } });
}
