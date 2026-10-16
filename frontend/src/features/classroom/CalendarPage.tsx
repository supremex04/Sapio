import { ChevronLeft, ChevronRight, Plus, Trash2 } from "lucide-react";
import { useState } from "react";
import { useNavigate } from "react-router";
import { errorMessage } from "../../shared/api/errors";
import { Emoji } from "../../shared/illustrations/Emoji";
import { Alert } from "../../shared/ui/Alert";
import { Button } from "../../shared/ui/Button";
import { Card } from "../../shared/ui/Card";
import { cx } from "../../shared/ui/cx";
import { DateTimePicker } from "../../shared/ui/DateTimePicker";
import { Dialog } from "../../shared/ui/Dialog";
import { PageTransition } from "../../shared/ui/PageTransition";
import { classroomApi } from "./api";
import { CalendarLine } from "./CalendarCard";
import { itemsOn, monthGrid, sameDay, upcoming } from "./calendar";
import type { CalendarItem, EventInput } from "./types";
import { useCalendar } from "./useCalendar";
import { useIsTeacher } from "./useRole";
import "./classroom.css";

const monthLabel = new Intl.DateTimeFormat(undefined, { month: "long", year: "numeric" });
const WEEKDAYS = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"];

/** The class calendar: teachers add events (each student sees them); students see them with their tests' due dates. */
export function CalendarPage() {
  const teacher = useIsTeacher();
  const navigate = useNavigate();
  const today = new Date();
  const [view, setView] = useState({ year: today.getFullYear(), month: today.getMonth() });
  const { items, error, reload } = useCalendar(view.year, view.month);
  const [editing, setEditing] = useState<{ item: CalendarItem | null; day: Date } | null>(null);
  const grid = monthGrid(view.year, view.month);

  const step = (delta: number) =>
    setView((v) => {
      const d = new Date(v.year, v.month + delta, 1);
      return { year: d.getFullYear(), month: d.getMonth() };
    });

  return (
    <PageTransition>
      <div className="stack-lg">
        <header className="row-between page-head">
          <div>
            <h1 className="row">
              <Emoji name="calendar" size={40} /> Calendar
            </h1>
            <p className="muted">
              {teacher ? "Events you add appear in every student's calendar, and they are notified." : "Events from your teachers and when your tests are due."}
            </p>
          </div>
          {teacher && (
            <Button variant="primary" icon={<Plus size={16} />} onClick={() => setEditing({ item: null, day: today })}>
              New event
            </Button>
          )}
        </header>
        {error && <Alert>Could not load the calendar. Try again in a moment.</Alert>}

        <Card>
          <div className="row-between cal-month-head">
            <h2 className="card-title">{monthLabel.format(new Date(view.year, view.month, 1))}</h2>
            <span className="row">
              <Button size="sm" variant="ghost" onClick={() => setView({ year: today.getFullYear(), month: today.getMonth() })}>
                Today
              </Button>
              <button type="button" className="cal-step" onClick={() => step(-1)} aria-label="Previous month">
                <ChevronLeft size={18} />
              </button>
              <button type="button" className="cal-step" onClick={() => step(1)} aria-label="Next month">
                <ChevronRight size={18} />
              </button>
            </span>
          </div>
          <div className="cal-month" role="grid">
            {WEEKDAYS.map((d) => (
              <span key={d} className="cal-month-weekday">
                {d}
              </span>
            ))}
            {grid.map((day) => {
              const dayItems = itemsOn(items ?? [], day);
              return (
                <div
                  key={day.toISOString()}
                  className={cx("cal-month-day", day.getMonth() !== view.month && "is-other", sameDay(day, today) && "is-today")}
                  onDoubleClick={() => teacher && setEditing({ item: null, day })}
                >
                  <span className="cal-month-date">{day.getDate()}</span>
                  {dayItems.slice(0, 3).map((it) => (
                    <button
                      key={it.id}
                      type="button"
                      className={cx("cal-chip", it.kind === "TEST_DUE" && "is-test")}
                      onClick={() => (teacher && it.kind === "EVENT" ? setEditing({ item: it, day }) : it.link && navigate(it.link))}
                      title={it.title}
                    >
                      {it.title}
                    </button>
                  ))}
                  {dayItems.length > 3 && <span className="xs muted">+{dayItems.length - 3} more</span>}
                </div>
              );
            })}
          </div>
        </Card>

        <Card>
          <h2 className="card-title">Coming up</h2>
          {items && upcoming(items, today, 20).length === 0 && <p className="muted small">Nothing scheduled this month.</p>}
          <ul className="cal-list">
            {upcoming(items ?? [], today, 20).map((it) => (
              <li key={it.id}>
                <CalendarLine item={it} onClick={teacher && it.kind === "EVENT" ? () => setEditing({ item: it, day: new Date(it.startsAt) }) : undefined} />
              </li>
            ))}
          </ul>
        </Card>
      </div>
      {editing && (
        <EventDialog
          item={editing.item}
          day={editing.day}
          onClose={() => setEditing(null)}
          onSaved={() => {
            setEditing(null);
            void reload();
          }}
        />
      )}
    </PageTransition>
  );
}

function EventDialog({ item, day, onClose, onSaved }: { item: CalendarItem | null; day: Date; onClose: () => void; onSaved: () => void }) {
  const [title, setTitle] = useState(item?.title ?? "");
  const [description, setDescription] = useState(item?.description ?? "");
  const [startsAt, setStartsAt] = useState<string | null>(item?.startsAt ?? new Date(day.getFullYear(), day.getMonth(), day.getDate(), 9, 0).toISOString());
  const [endsAt, setEndsAt] = useState<string | null>(item?.endsAt ?? null);
  const [allDay, setAllDay] = useState(item?.allDay ?? false);
  const [busy, setBusy] = useState(false);
  const [error, setError] = useState<string | null>(null);

  async function save() {
    setBusy(true);
    setError(null);
    if (!startsAt) return;
    const start = new Date(startsAt);
    const end = endsAt ? new Date(endsAt) : null;
    const input: EventInput = {
      title,
      description: description || null,
      // All-day events run from midnight to the end of their last day.
      startsAt: allDay ? new Date(start.getFullYear(), start.getMonth(), start.getDate()).toISOString() : startsAt,
      endsAt: end ? (allDay ? new Date(end.getFullYear(), end.getMonth(), end.getDate(), 23, 59).toISOString() : endsAt) : null,
      allDay,
    };
    try {
      await (item ? classroomApi.updateEvent(item.id, input) : classroomApi.createEvent(input));
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  async function remove() {
    if (!item) return;
    setBusy(true);
    try {
      await classroomApi.deleteEvent(item.id);
      onSaved();
    } catch (e) {
      setError(errorMessage(e));
      setBusy(false);
    }
  }

  return (
    <Dialog
      open
      onClose={onClose}
      title={item ? "Edit event" : "New event"}
      footer={
        <>
          {item && (
            <Button variant="ghost" icon={<Trash2 size={16} />} onClick={() => void remove()} disabled={busy}>
              Delete
            </Button>
          )}
          <Button variant="primary" onClick={() => void save()} loading={busy} disabled={!title.trim() || !startsAt}>
            {item ? "Save" : "Add to calendar"}
          </Button>
        </>
      }
    >
      <div className="stack">
        {error && <Alert>{error}</Alert>}
        <label className="field">
          <span className="field-label">Title</span>
          <input className="input" value={title} onChange={(e) => setTitle(e.target.value)} maxLength={200} autoFocus placeholder="e.g. Unit 2 quiz in class" />
        </label>
        <div className="field">
          <span className="field-label">Starts</span>
          <DateTimePicker value={startsAt} onChange={setStartsAt} withTime={!allDay} clearable={false} defaultMinutes={9 * 60} label="Start" />
        </div>
        <label className="row small">
          <input type="checkbox" checked={allDay} onChange={(e) => setAllDay(e.target.checked)} /> All day
        </label>
        <div className="field">
          <span className="field-label">Ends (optional)</span>
          <DateTimePicker value={endsAt} onChange={setEndsAt} withTime={!allDay} placeholder="Same day" min={startsAt ? new Date(startsAt) : undefined} defaultMinutes={17 * 60} label="End" />
        </div>
        <label className="field">
          <span className="field-label">Details (optional)</span>
          <textarea className="input" rows={3} value={description} onChange={(e) => setDescription(e.target.value)} maxLength={2000} />
        </label>
        {!item && <p className="xs muted">Your students are notified and see it in their calendar.</p>}
      </div>
    </Dialog>
  );
}
