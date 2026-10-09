import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useRef, useState } from "react";
import { formatDuration } from "../../shared/lib/format";
import { Segmented } from "../../shared/ui/Segmented";
import { niceTicks } from "./chartScale";
import type { DailyActivity } from "./types";

const HEIGHT = 200;
const PAD = { top: 16, right: 8, bottom: 28, left: 34 };
const MAX_BAR = 24;
const weekday = new Intl.DateTimeFormat(undefined, { weekday: "short" });
const longDate = new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" });

const parseDay = (iso: string) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
};

/** Column with a 4px rounded data-end and a square baseline (dataviz mark spec). */
function barPath(x: number, y: number, w: number, h: number): string {
  if (h <= 0) return "";
  const r = Math.min(4, w / 2, h);
  return `M${x},${y + h} V${y + r} Q${x},${y} ${x + r},${y} H${x + w - r} Q${x + w},${y} ${x + w},${y + r} V${y + h} Z`;
}

/**
 * Active minutes per day: a single-series column chart (no legend: the title names it), with a
 * hairline reference for the daily goal, per-column hover/focus tooltips and a table view.
 */
export function ActivityChart({ days, goalMinutes }: { days: DailyActivity[]; goalMinutes: number | null }) {
  const [view, setView] = useState<"chart" | "table">("chart");
  const [width, setWidth] = useState(640);
  const [hover, setHover] = useState<number | null>(null);
  const wrap = useRef<HTMLDivElement>(null);

  useEffect(() => {
    const el = wrap.current;
    if (!el) return;
    const ro = new ResizeObserver(([entry]) => entry && setWidth(entry.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, [view]);

  const minutes = useMemo(() => days.map((d) => d.activeSeconds / 60), [days]);
  const ticks = niceTicks(Math.max(...minutes, goalMinutes ?? 0));
  const top = ticks.at(-1) ?? 1;
  const plotW = Math.max(0, width - PAD.left - PAD.right);
  const plotH = HEIGHT - PAD.top - PAD.bottom;
  const band = days.length ? plotW / days.length : 0;
  const barW = Math.max(4, Math.min(MAX_BAR, band * 0.6));
  const y = (v: number) => PAD.top + plotH - (v / top) * plotH;
  const hovered = hover != null ? days[hover] : undefined;

  return (
    <div className="activity-chart">
      <div className="row-between" style={{ marginBottom: "var(--space-3)" }}>
        <div>
          <h2 className="card-title">Active minutes</h2>
          <p className="muted small">Last {days.length} days</p>
        </div>
        <Segmented
          label="Display"
          value={view}
          onChange={setView}
          options={[
            { value: "chart", label: "Chart" },
            { value: "table", label: "Table" },
          ]}
        />
      </div>

      {view === "table" ? (
        <div style={{ overflowX: "auto" }}>
          <table className="data-table">
            <thead>
              <tr>
                <th>Day</th>
                <th>Active</th>
                <th>Questions</th>
                <th>Correct</th>
                <th>XP</th>
              </tr>
            </thead>
            <tbody>
              {[...days].reverse().map((d) => (
                <tr key={d.date}>
                  <td>{longDate.format(parseDay(d.date))}</td>
                  <td className="tabular">{formatDuration(d.activeSeconds)}</td>
                  <td className="tabular">{d.questionsAnswered}</td>
                  <td className="tabular">{d.correctAnswers}</td>
                  <td className="tabular">{d.xpEarned}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div ref={wrap} className="chart-wrap" onMouseLeave={() => setHover(null)}>
          <svg width={width} height={HEIGHT} role="img" aria-label={`Active minutes per day for the last ${days.length} days`}>
            {ticks.map((t) => (
              <g key={t}>
                <line x1={PAD.left} x2={width - PAD.right} y1={y(t)} y2={y(t)} stroke="var(--chart-grid)" strokeWidth={1} shapeRendering="crispEdges" />
                <text x={PAD.left - 8} y={y(t)} dy="0.32em" textAnchor="end" className="chart-tick">
                  {t}
                </text>
              </g>
            ))}
            {goalMinutes != null && goalMinutes <= top && (
              <g>
                <line x1={PAD.left} x2={width - PAD.right} y1={y(goalMinutes)} y2={y(goalMinutes)} className="chart-goal" strokeWidth={2} />
                <text x={width - PAD.right} y={y(goalMinutes) - 6} textAnchor="end" className="chart-tick chart-goal-label">
                  Goal {goalMinutes}m
                </text>
              </g>
            )}
            {days.map((d, i) => {
              const v = minutes[i] ?? 0;
              const cx = PAD.left + band * i + band / 2;
              const isToday = i === days.length - 1;
              const showLabel = days.length <= 7 || i % 2 === (days.length - 1) % 2;
              return (
                <g
                  key={d.date}
                  tabIndex={0}
                  role="img"
                  aria-label={`${longDate.format(parseDay(d.date))}: ${formatDuration(d.activeSeconds)} active`}
                  onMouseEnter={() => setHover(i)}
                  onFocus={() => setHover(i)}
                  onBlur={() => setHover(null)}
                  className="chart-col"
                >
                  <rect x={cx - band / 2} y={PAD.top} width={band} height={plotH} fill="transparent" />
                  {hover === i && <rect x={cx - band / 2 + 2} y={PAD.top} width={band - 4} height={plotH} rx={8} className="chart-hover-band" />}
                  <motion.path
                    d={barPath(cx - barW / 2, y(v), barW, PAD.top + plotH - y(v))}
                    className={isToday ? "chart-bar chart-bar-today" : "chart-bar"}
                    initial={{ opacity: 0, scaleY: 0 }}
                    animate={{ opacity: 1, scaleY: 1 }}
                    style={{ transformOrigin: `${cx}px ${PAD.top + plotH}px` }}
                    transition={{ duration: 0.5, delay: i * 0.025, ease: [0.22, 1, 0.36, 1] }}
                  />
                  {showLabel && (
                    <text x={cx} y={HEIGHT - 8} textAnchor="middle" className={isToday ? "chart-tick chart-today" : "chart-tick"}>
                      {isToday ? "Today" : weekday.format(parseDay(d.date))}
                    </text>
                  )}
                </g>
              );
            })}
          </svg>
          <AnimatePresence>
            {hovered && hover != null && (
              <motion.div
                className="chart-tooltip"
                initial={{ opacity: 0, y: 4 }}
                animate={{ opacity: 1, y: 0 }}
                exit={{ opacity: 0 }}
                transition={{ duration: 0.12 }}
                style={{
                  left: Math.min(Math.max(PAD.left + band * hover + band / 2, 80), width - 80),
                  top: Math.max(y(minutes[hover] ?? 0) - 12, 0),
                }}
              >
                <strong className="chart-tooltip-value">{formatDuration(hovered.activeSeconds)} active</strong>
                <span>{longDate.format(parseDay(hovered.date))}</span>
                <span className="muted">
                  {hovered.questionsAnswered} questions · {hovered.xpEarned} XP
                </span>
              </motion.div>
            )}
          </AnimatePresence>
        </div>
      )}
    </div>
  );
}
