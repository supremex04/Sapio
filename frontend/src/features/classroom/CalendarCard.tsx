import { ChevronLeft, ChevronRight } from "lucide-react";
import { useState } from "react";
import { Link } from "react-router";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Card } from "../../shared/ui/Card";
import { cx } from "../../shared/ui/cx";
import { itemsOn, monthGrid, sameDay, upcoming, whenLabel } from "./calendar";
import type { CalendarItem } from "./types";
import { useCalendar } from "./useCalendar";
import "./classroom.css";

const monthLabel = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });
const WEEKDAYS = ["M", "T", "W", "T", "F", "S", "S"];

/** The right rail's calendar: a small month (dots on busy days) and what is coming up, or a chosen day. */
export function CalendarCard() {
  const today = new Date();
  const [view, setView] = useState({ year: today.getFullYear(), month: today.getMonth() });
  const [picked, setPicked] = useState<Date | null>(null);
  const { items } = useCalendar(view.year, view.month);
  const grid = monthGrid(view.year, view.month);
  const shown = picked ? itemsOn(items ?? [], picked) : upcoming(items ?? [], today, 4);

  const step = (delta: number) => {
    setPicked(null);
    setView((v) => {
      const d = new Date(v.year, v.month + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });
  };

  return (
    <Card tight className="cal-card">
      <div className="row-between">
        <Link to="/calendar" className="cal-card-title">
          <Emoji name="calendar" size={24} /> <strong>{monthLabel.format(new Date(view.year, view.month, 1))}</strong>
        </Link>
        <span className="row" style={{ gap: 2 }}>
          <button type="button" className="cal-step" onClick={() => step(-1)} aria-label="Previous month">
            <ChevronLeft size={16} />
          </button>
          <button type="button" className="cal-step" onClick={() => step(1)} aria-label="Next month">
            <ChevronRight size={16} />
          </button>
        </span>
      </div>
      <div className="cal-mini" role="grid" aria-label="Month">
        {WEEKDAYS.map((d, i) => (
          <span key={i} className="cal-mini-head" aria-hidden>
            {d}
          </span>
        ))}
        {grid.map((day) => {
          const dayItems = itemsOn(items ?? [], day);
          return (
            <button
              key={day.toISOString()}
              type="button"
              className={cx("cal-mini-day", day.getMonth() !== view.month && "is-other", sameDay(day, today) && "is-today", picked && sameDay(day, picked) && "is-picked")}
              onClick={() => setPicked((p) => (p && sameDay(p, day) ? null : day))}
              aria-label={`${day.toDateString()}${dayItems.length ? `, ${dayItems.length} items` : ""}`}
            >
              {day.getDate()}
              {dayItems.length > 0 && (
                <span className="cal-dots" aria-hidden>
                  {dayItems.slice(0, 3).map((it) => (
                    <i key={it.id} className={it.kind === "TEST_DUE" ? "is-test" : ""} />
                  ))}
                </span>
              )}
            </button>
          );
        })}
      </div>
      <p className="eyebrow cal-card-sub">{picked ? picked.toDateString() : "Coming up"}</p>
      {items && shown.length === 0 && <p className="xs muted">{picked ? "Nothing that day." : "Nothing scheduled."}</p>}
      <ul className="cal-list">
        {shown.map((it) => (
          <li key={it.id}>
            <CalendarLine item={it} />
          </li>
        ))}
      </ul>
    </Card>
  );
}

export function CalendarLine({ item, onClick }: { item: CalendarItem; onClick?: () => void }) {
  const body = (
    <>
      <span className={cx("cal-bar", item.kind === "TEST_DUE" && "is-test")} aria-hidden />
      <span className="cal-line-text">
        <strong className="small">{item.title}</strong>
        <span className="xs muted">
          {whenLabel(item)} · {item.teacherName}
        </span>
      </span>
    </>
  );
  if (onClick) {
    return (
      <button type="button" className="cal-line" onClick={onClick}>
        {body}
      </button>
    );
  }
  return item.link ? (
    <Link to={item.link} className="cal-line">
      {body}
    </Link>
  ) : (
    <div className="cal-line">{body}</div>
  );
}
