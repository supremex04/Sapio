import { motion } from "motion/react";
import type { ReactNode } from "react";
import { cx } from "./cx";

const clamp01 = (v: number) => Math.min(1, Math.max(0, Number.isFinite(v) ? v : 0));

interface ProgressBarProps {
  value: number; // 0..1
  color?: string; // CSS color, defaults to primary
  size?: "sm" | "md" | "lg";
  label: string; // accessible label
}

export function ProgressBar({ value, color, size = "md", label }: ProgressBarProps) {
  const v = clamp01(value);
  return (
    <div
      className={cx("progress", size !== "md" && `progress-${size}`)}
      role="progressbar"
      aria-label={label}
      aria-valuemin={0}
      aria-valuemax={100}
      aria-valuenow={Math.round(v * 100)}
      style={color ? ({ "--progress-color": color } as React.CSSProperties) : undefined}
    >
      <motion.div className="progress-fill" initial={{ width: 0 }} animate={{ width: `${v * 100}%` }} transition={{ duration: 0.6, ease: [0.22, 1, 0.36, 1] }} />
    </div>
  );
}

interface ProgressRingProps {
  value: number; // 0..1
  size?: number;
  stroke?: number;
  color?: string;
  label: string;
  children?: ReactNode;
}

export function ProgressRing({ value, size = 120, stroke = 10, color, label, children }: ProgressRingProps) {
  const v = clamp01(value);
  const r = (size - stroke) / 2;
  const c = 2 * Math.PI * r;
  return (
    <div
      className="ring"
      style={{ width: size, height: size, ...(color ? ({ "--ring-color": color } as React.CSSProperties) : {}) }}
      role="img"
      aria-label={`${label}: ${Math.round(v * 100)}%`}
    >
      <svg width={size} height={size} viewBox={`0 0 ${size} ${size}`} aria-hidden>
        <circle className="ring-track" cx={size / 2} cy={size / 2} r={r} fill="none" strokeWidth={stroke} />
        <motion.circle
          className="ring-fill"
          cx={size / 2}
          cy={size / 2}
          r={r}
          fill="none"
          strokeWidth={stroke}
          strokeDasharray={c}
          initial={{ strokeDashoffset: c }}
          animate={{ strokeDashoffset: c * (1 - v) }}
          transition={{ duration: 1, ease: [0.22, 1, 0.36, 1] }}
        />
      </svg>
      <div className="ring-label">{children}</div>
    </div>
  );
}
