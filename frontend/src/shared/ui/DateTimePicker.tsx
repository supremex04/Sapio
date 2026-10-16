import { CalendarDays, ChevronLeft, ChevronRight, X } from "lucide-react";
import { useEffect, useId, useRef, useState } from "react";
import { cx } from "./cx";
import { combine, minutesOf, monthGrid, sameDay, timeLabel, timeOptions, valueLabel } from "./dateTime";
import "./dateTimePicker.css";

const monthLabel = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });
const WEEKDAYS = ["M", "T", "W", "T", "F", "S", "S"];
const PRESETS = [
  { label: "Morning", minutes: 9 * 60 },
  { label: "Noon", minutes: 12 * 60 },
  { label: "End of day", minutes: 23 * 60 + 59 },
];

interface Props {
  /** ISO instant, or null for none. */
  value: string | null;
  onChange: (value: string | null) => void;
  /** Shown when there is no value, e.g. "No due date". */
  placeholder?: string;
  /** Pick a time as well as a day (default true). */
  withTime?: boolean;
  /** Time used when a day is picked first, in minutes past midnight (default: end of day). */
  defaultMinutes?: number;
  /** Days before this one cannot be picked. */
  min?: Date;
  clearable?: boolean;
  label?: string;
}

/**
 * Date (and time) picker in the design system's style, replacing the browser's native control: a button with the
 * chosen value; it opens a month grid and quick times in place (no floating layer, so it is never clipped inside
 * dialogs). Escape or a click outside closes it.
 */
export function DateTimePicker({ value, onChange, placeholder = "Pick a date", withTime = true, defaultMinutes = 23 * 60 + 59, min, clearable = true, label }: Props) {
  const [open, setOpen] = useState(false);
  const selected = value ? new Date(value) : null;
  const [view, setView] = useState(() => {
    const base = selected ?? new Date();
    return { year: base.getFullYear(), month: base.getMonth() };
  });
  const ref = useRef<HTMLDivElement>(null);
  const panelId = useId();
  const today = new Date();
  const minDay = min ? new Date(min.getFullYear(), min.getMonth(), min.getDate()) : null;
  const minutes = value ? minutesOf(value) : defaultMinutes;

  useEffect(() => {
    if (!open) return;
    const away = (e: PointerEvent) => !ref.current?.contains(e.target as Node) && setOpen(false);
    const key = (e: KeyboardEvent) => {
      if (e.key === "Escape") {
        e.stopPropagation(); // do not close a surrounding dialog
        setOpen(false);
      }
    };
    document.addEventListener("pointerdown", away);
    ref.current?.addEventListener("keydown", key);
    const node = ref.current;
    return () => {
      document.removeEventListener("pointerdown", away);
      node?.removeEventListener("keydown", key);
    };
  }, [open]);

  const step = (delta: number) =>
    setView((v) => {
      const d = new Date(v.year, v.month + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });

  const pickDay = (day: Date) => onChange(combine(day, withTime ? minutes : 0));
  const pickTime = (m: number) => onChange(combine(selected ?? today, m));

  return (
    <div className="dtp" ref={ref}>
      <div className="dtp-field">
        <button
          type="button"
          className={cx("dtp-trigger", !value && "is-empty", open && "is-open")}
          aria-expanded={open}
          aria-controls={panelId}
          aria-label={label ? `${label}: ${value ? valueLabel(value, withTime) : placeholder}` : undefined}
          onClick={() => setOpen((o) => !o)}
        >
          <CalendarDays size={16} aria-hidden />
          <span>{value ? valueLabel(value, withTime) : placeholder}</span>
        </button>
        {clearable && value && (
          <button type="button" className="dtp-clear" onClick={() => onChange(null)} aria-label="Clear date">
            <X size={14} />
          </button>
        )}
      </div>

      {open && (
        <div className="dtp-panel" id={panelId} role="dialog" aria-label={label ?? "Choose a date"}>
          <div className="dtp-head">
            <strong>{monthLabel.format(new Date(view.year, view.month, 1))}</strong>
            <span className="dtp-steps">
              <button type="button" className="dtp-step" onClick={() => step(-1)} aria-label="Previous month">
                <ChevronLeft size={16} />
              </button>
              <button type="button" className="dtp-step" onClick={() => step(1)} aria-label="Next month">
                <ChevronRight size={16} />
              </button>
            </span>
          </div>
          <div className="dtp-grid" role="grid">
            {WEEKDAYS.map((d, i) => (
              <span key={i} className="dtp-weekday" aria-hidden>
                {d}
              </span>
            ))}
            {monthGrid(view.year, view.month).map((day) => {
              const disabled = !!minDay && day < minDay;
              return (
                <button
                  key={day.toISOString()}
                  type="button"
                  disabled={disabled}
                  className={cx("dtp-day", day.getMonth() !== view.month && "is-other", sameDay(day, today) && "is-today", selected && sameDay(day, selected) && "is-selected")}
                  onClick={() => pickDay(day)}
                  aria-pressed={!!selected && sameDay(day, selected)}
                  aria-label={day.toDateString()}
                >
                  {day.getDate()}
                </button>
              );
            })}
          </div>

          {withTime && (
            <div className="dtp-time">
              <span className="dtp-time-label">Time</span>
              <div className="dtp-presets">
                {PRESETS.map((p) => (
                  <button key={p.label} type="button" className={cx("dtp-preset", value && minutes === p.minutes && "is-selected")} onClick={() => pickTime(p.minutes)}>
                    {p.label}
                  </button>
                ))}
              </div>
              <select className="dtp-select" value={minutes} onChange={(e) => pickTime(Number(e.target.value))} aria-label="Time">
                {!timeOptions().includes(minutes) && <option value={minutes}>{timeLabel(minutes)}</option>}
                {timeOptions().map((m) => (
                  <option key={m} value={m}>
                    {timeLabel(m)}
                  </option>
                ))}
              </select>
            </div>
          )}

          <div className="dtp-foot">
            {clearable ? (
              <button type="button" className="dtp-link" onClick={() => onChange(null)} disabled={!value}>
                Clear
              </button>
            ) : (
              <span />
            )}
            <button type="button" className="dtp-done" onClick={() => setOpen(false)}>
              Done
            </button>
          </div>
        </div>
      )}
    </div>
  );
}
