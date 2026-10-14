import { ChartColumn, Grip } from "lucide-react";
import { AnimatePresence, motion } from "motion/react";
import { useEffect, useMemo, useRef, useState, type KeyboardEvent } from "react";
import { formatDuration } from "../../shared/lib/format";
import type { DailyActivity } from "./types";

/** Days Home fetches: enough for the longest range (a year). */
export const ACTIVITY_DAYS = 365;

const RANGES = [
  { days: 30, label: "30 days" },
  { days: 60, label: "60 days" },
  { days: 182, label: "6 months" },
  { days: 365, label: "1 year" },
] as const;
type RangeDays = (typeof RANGES)[number]["days"];
type View = "dots" | "bars";

const weekday = new Intl.DateTimeFormat(undefined, { weekday: "short" });
const monthName = new Intl.DateTimeFormat(undefined, { month: "short" });
const longDate = new Intl.DateTimeFormat(undefined, { weekday: "long", month: "short", day: "numeric" });

const parseDay = (iso: string) => {
  const [y, m, d] = iso.split("-").map(Number);
  return new Date(y ?? 1970, (m ?? 1) - 1, d ?? 1);
};
/** Monday = 0 … Sunday = 6. */
const weekdayIndex = (iso: string) => (parseDay(iso).getDay() + 6) % 7;

/**
 * Intensity 0-4, relative to the daily goal when there is one (so level 3 means "goal met"),
 * otherwise to a 15-minute reference.
 */
export function level(activeSeconds: number, goalMinutes: number | null): 0 | 1 | 2 | 3 | 4 {
  const minutes = activeSeconds / 60;
  if (minutes <= 0) return 0;
  const goal = goalMinutes && goalMinutes > 0 ? goalMinutes : 15;
  if (minutes >= goal * 1.5) return 4;
  if (minutes >= goal) return 3;
  if (minutes >= goal / 2) return 2;
  return 1;
}

/** Headline numbers for a range: active days, total time, days the goal was met, longest run of active days. */
export function summarize(days: DailyActivity[], goalMinutes: number | null) {
  let streak = 0;
  let longest = 0;
  for (const d of days) {
    streak = d.activeSeconds > 0 ? streak + 1 : 0;
    longest = Math.max(longest, streak);
  }
  return {
    activeDays: days.filter((d) => d.activeSeconds > 0).length,
    totalSeconds: days.reduce((sum, d) => sum + d.activeSeconds, 0),
    goalDays: goalMinutes ? days.filter((d) => d.activeSeconds >= goalMinutes * 60).length : null,
    longest,
  };
}

/** A per-viewer preference; storage may be unavailable, so every access is guarded. */
function remembered<T extends string | number>(key: string, allowed: readonly T[], fallback: T): [T, (v: T) => void] {
  const read = () => {
    try {
      const raw = localStorage.getItem(key);
      const found = allowed.find((a) => String(a) === raw);
      return found ?? fallback;
    } catch {
      return fallback;
    }
  };
  const write = (v: T) => {
    try {
      localStorage.setItem(key, String(v));
    } catch {
      /* not persisted: fine */
    }
  };
  return [read(), write];
}

function useRemembered<T extends string | number>(key: string, allowed: readonly T[], fallback: T) {
  const [state, setState] = useState<T>(() => remembered(key, allowed, fallback)[0]);
  const set = (v: T) => {
    setState(v);
    remembered(key, allowed, fallback)[1](v);
  };
  return [state, set] as const;
}

function useWidth<T extends HTMLElement>() {
  const ref = useRef<T>(null);
  const [width, setWidth] = useState(0);
  useEffect(() => {
    const el = ref.current;
    if (!el) return;
    setWidth(el.clientWidth);
    const ro = new ResizeObserver(([entry]) => entry && setWidth(entry.contentRect.width));
    ro.observe(el);
    return () => ro.disconnect();
  }, []);
  return [ref, width] as const;
}

/** Dots never grow past this (px per week column); short ranges get the numbers beside them instead. */
const MAX_STEP = 28;
const STATS_MIN_W = 380;

/**
 * Activity on Home: the last 30 days by default (60 days, 6 months or a year on request), as dots
 * (one column per week, darker = more time, full colour = daily goal met) or as quiet bars, with the
 * range's headline numbers alongside.
 */
export function ActivityChart({ days, goalMinutes }: { days: DailyActivity[]; goalMinutes: number | null }) {
  const [range, setRange] = useRemembered<RangeDays>("sapio.activity.range", RANGES.map((r) => r.days), 30);
  const [view, setView] = useRemembered<View>("sapio.activity.view", ["dots", "bars"], "dots");
  const [ref, width] = useWidth<HTMLDivElement>();
  const shown = useMemo(() => days.slice(-range), [days, range]);
  const stats = summarize(shown, goalMinutes);

  // Short ranges of dots are compact, so the numbers sit beside them; long ranges, bars and phones stack.
  const weeks = Math.ceil(((shown[0] ? weekdayIndex(shown[0].date) : 0) + shown.length) / 7);
  const naturalDots = LABEL_W + weeks * MAX_STEP;
  const side = view === "dots" && width >= naturalDots + STATS_MIN_W;
  const chartW = side ? naturalDots : width;

  return (
    <div className="activity">
      <div className="activity-head">
        <h2 className="card-title">Activity</h2>
        <div className="activity-controls">
          <div className="activity-switch" role="group" aria-label="Time range">
            {RANGES.map((r) => (
              <button key={r.days} type="button" aria-pressed={range === r.days} onClick={() => setRange(r.days)}>
                {r.label}
              </button>
            ))}
          </div>
          <div className="activity-switch activity-switch--icons" role="group" aria-label="Display">
            <button type="button" aria-pressed={view === "dots"} onClick={() => setView("dots")} title="Dots">
              <Grip size={15} aria-hidden />
              <span className="sr-only">Dots</span>
            </button>
            <button type="button" aria-pressed={view === "bars"} onClick={() => setView("bars")} title="Bars">
              <ChartColumn size={15} aria-hidden />
              <span className="sr-only">Bars</span>
            </button>
          </div>
        </div>
      </div>

      <div ref={ref} className={side ? "activity-body activity-body--side" : "activity-body"}>
        {width > 0 && (
          <>
            <div className="activity-chart" style={{ width: chartW }}>
              {view === "dots" ? (
                <Dots days={shown} goalMinutes={goalMinutes} width={chartW} />
              ) : (
                <Bars days={shown} goalMinutes={goalMinutes} width={chartW} />
              )}
            </div>
            <dl className={side ? "activity-stats" : "activity-stats activity-stats--row"}>
              <Stat label="Active days" value={`${stats.activeDays}`} sub={`of ${shown.length}`} />
              <Stat label="Time studied" value={stats.totalSeconds ? formatDuration(stats.totalSeconds) : "0 min"} />
              {stats.goalDays != null && <Stat label="Goal met" value={`${stats.goalDays}`} sub={`${stats.goalDays === 1 ? "day" : "days"} at ${goalMinutes}+ min`} />}
              <Stat label="Longest run" value={`${stats.longest}`} sub={stats.longest === 1 ? "day" : "days"} />
            </dl>
          </>
        )}
      </div>
    </div>
  );
}

function Stat({ label, value, sub }: { label: string; value: string; sub?: string }) {
  return (
    <div className="activity-stat">
      <dt>{label}</dt>
      <dd>
        <strong>{value}</strong>
        {sub && <span> {sub}</span>}
      </dd>
    </div>
  );
}

function Tooltip({ x, y, width, day }: { x: number; y: number; width: number; day: DailyActivity }) {
  return (
    <motion.div
      className="chart-tooltip"
      role="status"
      initial={{ opacity: 0, y: 4 }}
      animate={{ opacity: 1, y: 0 }}
      exit={{ opacity: 0 }}
      transition={{ duration: 0.12 }}
      style={{ left: Math.min(Math.max(x, 90), Math.max(width - 90, 90)), top: y }}
    >
      <strong className="chart-tooltip-value">{day.activeSeconds > 0 ? `${formatDuration(day.activeSeconds)} active` : "No activity"}</strong>
      <span>{longDate.format(parseDay(day.date))}</span>
      {day.questionsAnswered > 0 && (
        <span className="muted">
          {day.questionsAnswered} questions · {day.xpEarned} XP
        </span>
      )}
    </motion.div>
  );
}

// ---------------------------------------------------------------------------------------------------
// Dots: one column per week, Monday at the top

const LABEL_W = 30;
const MONTH_H = 18;

function Dots({ days, goalMinutes, width }: { days: DailyActivity[]; goalMinutes: number | null; width: number }) {
  const [active, setActive] = useState<number | null>(null);
  const lead = days[0] ? weekdayIndex(days[0].date) : 0; // the first column may start mid-week
  const weeks = Math.ceil((lead + days.length) / 7);
  const step = Math.max(8, Math.min(MAX_STEP, (width - LABEL_W) / Math.max(weeks, 1)));
  const r = step * 0.36;

  const months = useMemo(() => {
    const out: { col: number; label: string }[] = [];
    days.forEach((d, i) => {
      const date = parseDay(d.date);
      if (date.getDate() !== 1 && i !== 0) return;
      const col = Math.floor((i + lead) / 7);
      if (out.length && col - out.at(-1)!.col < 3) return;
      out.push({ col, label: monthName.format(date) });
    });
    return out;
  }, [days, lead]);

  const center = (i: number) => ({
    x: LABEL_W + Math.floor((i + lead) / 7) * step + step / 2,
    y: MONTH_H + ((i + lead) % 7) * step + step / 2,
  });
  const svgW = LABEL_W + weeks * step;
  const current = active != null ? days[active] : undefined;

  const onKey = (e: KeyboardEvent) => {
    const delta = { ArrowLeft: -7, ArrowRight: 7, ArrowUp: -1, ArrowDown: 1 }[e.key];
    if (delta == null) return;
    e.preventDefault();
    setActive((a) => Math.max(0, Math.min(days.length - 1, (a ?? days.length - 1) + delta)));
  };

  return (
    <div className="dots" onMouseLeave={() => setActive(null)}>
      <svg
        width={svgW}
        height={MONTH_H + 7 * step}
        tabIndex={0}
        role="img"
        aria-label={`Activity over the last ${days.length} days. Use the arrow keys to read individual days.`}
        onKeyDown={onKey}
        onFocus={() => setActive((a) => a ?? days.length - 1)}
        onBlur={() => setActive(null)}
      >
        {months.map((m) => (
          <text key={`${m.col}-${m.label}`} x={LABEL_W + m.col * step + step / 2 - r} y={12} className="dots-label">
            {m.label}
          </text>
        ))}
        {[0, 2, 4].map((row) => (
          <text key={row} x={0} y={MONTH_H + row * step + step / 2} dy="0.35em" className="dots-label">
            {weekday.format(new Date(2024, 0, 1 + row))}
          </text>
        ))}
        {days.map((d, i) => {
          const { x, y } = center(i);
          const isToday = i === days.length - 1;
          return (
            <circle
              key={d.date}
              cx={x}
              cy={y}
              r={r}
              className={`dot dot-${level(d.activeSeconds, goalMinutes)}${isToday ? " dot-today" : ""}${active === i ? " dot-active" : ""}`}
              onMouseEnter={() => setActive(i)}
            />
          );
        })}
      </svg>
      <div className="dots-legend" aria-hidden>
        Less
        {[0, 1, 2, 3, 4].map((l) => (
          <svg key={l} width={10} height={10}>
            <circle cx={5} cy={5} r={4.5} className={`dot dot-${l}`} />
          </svg>
        ))}
        More
      </div>
      <AnimatePresence>
        {current && active != null && <Tooltip x={center(active).x} y={center(active).y - r - 4} width={svgW} day={current} />}
      </AnimatePresence>
    </div>
  );
}

// ---------------------------------------------------------------------------------------------------
// Bars: minutes per day, quiet

const BAR_H = 170;
const PAD = { top: 22, bottom: 24 };

function Bars({ days, goalMinutes, width }: { days: DailyActivity[]; goalMinutes: number | null; width: number }) {
  const [hover, setHover] = useState<number | null>(null);
  const minutes = days.map((d) => d.activeSeconds / 60);
  const top = Math.max(...minutes, (goalMinutes ?? 0) * 1.25, 10);
  const plotH = BAR_H - PAD.top - PAD.bottom;
  const band = days.length ? width / days.length : 0;
  const barW = Math.max(1.5, Math.min(12, band * 0.5));
  const y = (v: number) => PAD.top + plotH - (v / top) * plotH;
  const hovered = hover != null ? days[hover] : undefined;
  const daily = days.length <= 31;

  return (
    <div className="bars" onMouseLeave={() => setHover(null)}>
      <svg width={width} height={BAR_H} role="img" aria-label={`Active minutes per day for the last ${days.length} days`}>
        <line x1={0} x2={width} y1={PAD.top + plotH + 0.5} y2={PAD.top + plotH + 0.5} className="bars-base" />
        {goalMinutes != null && (
          <g className="bars-goal">
            <line x1={0} x2={width} y1={y(goalMinutes)} y2={y(goalMinutes)} />
            <text x={0} y={y(goalMinutes) - 6} className="dots-label">
              Goal {goalMinutes} min
            </text>
          </g>
        )}
        {days.map((d, i) => {
          const v = minutes[i] ?? 0;
          const cx = band * i + band / 2;
          const h = v > 0 ? Math.max(3, PAD.top + plotH - y(v)) : 0;
          const isToday = i === days.length - 1;
          const date = parseDay(d.date);
          // Weekday labels every few days on short ranges; month starts on long ones.
          const label = isToday && daily ? "Today" : daily ? ((days.length - 1 - i) % 3 === 0 ? weekday.format(date) : null) : date.getDate() === 1 ? monthName.format(date) : null;
          return (
            <g key={d.date} onMouseEnter={() => setHover(i)} className="bars-col">
              <rect x={cx - band / 2} y={0} width={band} height={BAR_H} fill="transparent" />
              {v > 0 && (
                <motion.rect
                  x={cx - barW / 2}
                  y={PAD.top + plotH - h}
                  width={barW}
                  height={h}
                  rx={Math.min(barW / 2, 4)}
                  className={`bar dot-${level(d.activeSeconds, goalMinutes)}${hover === i ? " dot-active" : ""}`}
                  initial={{ scaleY: 0 }}
                  animate={{ scaleY: 1 }}
                  style={{ transformOrigin: `${cx}px ${PAD.top + plotH}px` }}
                  transition={{ duration: 0.45, delay: Math.min(i * 0.015, 0.4), ease: [0.22, 1, 0.36, 1] }}
                />
              )}
              {label && (
                <text x={cx} y={BAR_H - 6} textAnchor="middle" className={isToday ? "dots-label dots-today" : "dots-label"}>
                  {label}
                </text>
              )}
            </g>
          );
        })}
      </svg>
      <AnimatePresence>
        {hovered && hover != null && <Tooltip x={band * hover + band / 2} y={y(minutes[hover] ?? 0) - 8} width={width} day={hovered} />}
      </AnimatePresence>
    </div>
  );
}
