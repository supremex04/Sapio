import { motion } from "motion/react";
import { useId, type KeyboardEvent, type ReactNode } from "react";

export interface TabItem<T extends string> {
  value: T;
  label: ReactNode;
}

/** Accessible tab list (arrow-key navigation) with an animated indicator. Render panels yourself. */
export function Tabs<T extends string>({ value, items, onChange, label }: { value: T; items: TabItem<T>[]; onChange: (v: T) => void; label: string }) {
  const layoutId = useId();
  function onKeyDown(e: KeyboardEvent) {
    if (e.key !== "ArrowRight" && e.key !== "ArrowLeft") return;
    const i = items.findIndex((t) => t.value === value);
    const next = items[(i + (e.key === "ArrowRight" ? 1 : items.length - 1)) % items.length];
    if (next) onChange(next.value);
  }
  return (
    <div className="tabs" role="tablist" aria-label={label} onKeyDown={onKeyDown}>
      {items.map((t) => (
        <button key={t.value} type="button" role="tab" aria-selected={t.value === value} tabIndex={t.value === value ? 0 : -1} onClick={() => onChange(t.value)}>
          {t.label}
          {t.value === value && <motion.span layoutId={layoutId} className="tab-indicator" />}
        </button>
      ))}
    </div>
  );
}
