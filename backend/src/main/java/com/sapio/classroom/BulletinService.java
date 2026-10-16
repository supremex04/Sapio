package com.sapio.classroom;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.notification.NotificationService;
import com.sapio.user.User;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Notices and the class calendar. A notice goes to every student's notifications; an event appears in each
 * student's calendar (and is announced). Students' calendars also show when their assigned tests are due.
 */
@Service
public class BulletinService {

    public record Notice(UUID id, String teacherName, String title, String body, Instant createdAt) {
    }

    public enum ItemKind { EVENT, TEST_DUE }

    /** A calendar entry; {@code link} opens the test for TEST_DUE. */
    public record CalendarItem(UUID id, ItemKind kind, String title, String description, Instant startsAt,
                               Instant endsAt, boolean allDay, String teacherName, String link) {
    }

    public record EventInput(String title, String description, Instant startsAt, Instant endsAt, boolean allDay) {
    }

    private final JdbcTemplate jdbc;
    private final RosterService roster;
    private final NotificationService notifications;
    private final Clock clock;

    BulletinService(JdbcTemplate jdbc, RosterService roster, NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.roster = roster;
        this.notifications = notifications;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------- notices

    @Transactional
    public Notice postNotice(UUID teacherId, String title, String body) {
        User teacher = roster.requireTeacher(teacherId);
        String t = required(title, 200, "A notice needs a title");
        String b = required(body, 5000, "A notice needs a message");
        UUID id = UUID.randomUUID();
        Instant now = clock.instant();
        jdbc.update("INSERT INTO class_notices (id, teacher_id, title, body, created_at) VALUES (?, ?, ?, ?, ?)", id,
                teacherId, t, b, Timestamp.from(now));
        notifications.postAll(roster.students(teacherId), NotificationService.Type.NOTICE, t,
                b.length() > 280 ? b.substring(0, 277) + "..." : b, "/notices", teacher.getFullName());
        return new Notice(id, teacher.getFullName(), t, b, now);
    }

    public List<Notice> teacherNotices(UUID teacherId) {
        roster.requireTeacher(teacherId);
        return notices("n.teacher_id = ?", teacherId);
    }

    /** Notices from the student's teachers. */
    public List<Notice> studentNotices(UUID studentId) {
        return notices("n.teacher_id IN (SELECT teacher_id FROM class_memberships WHERE student_id = ? AND "
                + "status = 'ACCEPTED')", studentId);
    }

    public void deleteNotice(UUID teacherId, UUID noticeId) {
        roster.requireTeacher(teacherId);
        if (jdbc.update("DELETE FROM class_notices WHERE id = ? AND teacher_id = ?", noticeId, teacherId) == 0) {
            throw ApplicationException.notFound("Notice");
        }
    }

    private List<Notice> notices(String where, UUID id) {
        return jdbc.query("SELECT n.*, u.full_name FROM class_notices n JOIN users u ON u.id = n.teacher_id WHERE "
                        + where + " ORDER BY n.created_at DESC LIMIT 100",
                (rs, i) -> new Notice(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("title"),
                        rs.getString("body"), rs.getTimestamp("created_at").toInstant()), id);
    }

    // ------------------------------------------------------------------------------- calendar

    @Transactional
    public CalendarItem createEvent(UUID teacherId, EventInput input) {
        User teacher = roster.requireTeacher(teacherId);
        EventInput e = validate(input);
        UUID id = UUID.randomUUID();
        jdbc.update("""
                        INSERT INTO class_events (id, teacher_id, title, description, starts_at, ends_at, all_day, created_at)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)""", id, teacherId, e.title(), e.description(),
                Timestamp.from(e.startsAt()), e.endsAt() == null ? null : Timestamp.from(e.endsAt()), e.allDay(),
                Timestamp.from(clock.instant()));
        notifications.postAll(roster.students(teacherId), NotificationService.Type.EVENT, "New in your calendar: "
                + e.title(), null, "/calendar", teacher.getFullName());
        return event(teacherId, id);
    }

    public CalendarItem updateEvent(UUID teacherId, UUID eventId, EventInput input) {
        roster.requireTeacher(teacherId);
        EventInput e = validate(input);
        if (jdbc.update("""
                        UPDATE class_events SET title = ?, description = ?, starts_at = ?, ends_at = ?, all_day = ?
                        WHERE id = ? AND teacher_id = ?""", e.title(), e.description(), Timestamp.from(e.startsAt()),
                e.endsAt() == null ? null : Timestamp.from(e.endsAt()), e.allDay(), eventId, teacherId) == 0) {
            throw ApplicationException.notFound("Event");
        }
        return event(teacherId, eventId);
    }

    public void deleteEvent(UUID teacherId, UUID eventId) {
        roster.requireTeacher(teacherId);
        if (jdbc.update("DELETE FROM class_events WHERE id = ? AND teacher_id = ?", eventId, teacherId) == 0) {
            throw ApplicationException.notFound("Event");
        }
    }

    /** The teacher's own events, plus the due dates of the tests they sent. */
    public List<CalendarItem> teacherCalendar(UUID teacherId, Instant from, Instant to) {
        roster.requireTeacher(teacherId);
        List<CalendarItem> items = new ArrayList<>(events("e.teacher_id = ?", teacherId, from, to));
        jdbc.query("""
                SELECT t.id, t.title, t.due_at, u.full_name FROM class_tests t JOIN users u ON u.id = t.teacher_id
                WHERE t.teacher_id = ? AND t.status = 'SENT' AND t.due_at >= ? AND t.due_at < ?""", rs -> {
                    items.add(new CalendarItem(rs.getObject(1, UUID.class), ItemKind.TEST_DUE, rs.getString(2) + " is due",
                            null, rs.getTimestamp(3).toInstant(), null, false, rs.getString(4),
                            "/class-tests/" + rs.getObject(1, UUID.class)));
                }, teacherId, Timestamp.from(from), Timestamp.from(to));
        items.sort(Comparator.comparing(CalendarItem::startsAt));
        return items;
    }

    /** Events from the student's teachers, plus their assigned tests that are due (not yet completed). */
    public List<CalendarItem> studentCalendar(UUID studentId, Instant from, Instant to) {
        List<CalendarItem> items = new ArrayList<>(events("e.teacher_id IN (SELECT teacher_id FROM class_memberships "
                + "WHERE student_id = ? AND status = 'ACCEPTED')", studentId, from, to));
        jdbc.query("""
                SELECT a.id, a.title, a.due_at, a.assigned_by FROM assessments a
                WHERE a.user_id = ? AND a.assigned_by IS NOT NULL AND a.status = 'IN_PROGRESS'
                  AND a.due_at >= ? AND a.due_at < ?""", rs -> {
                    items.add(new CalendarItem(rs.getObject(1, UUID.class), ItemKind.TEST_DUE, rs.getString(2) + " is due",
                            null, rs.getTimestamp(3).toInstant(), null, false, rs.getString(4),
                            "/assessments/" + rs.getObject(1, UUID.class)));
                }, studentId, Timestamp.from(from), Timestamp.from(to));
        items.sort(Comparator.comparing(CalendarItem::startsAt));
        return items;
    }

    private CalendarItem event(UUID teacherId, UUID eventId) {
        return events("e.teacher_id = ? AND e.id = ?", new Object[] {teacherId, eventId}, Instant.EPOCH,
                Instant.parse("9999-01-01T00:00:00Z")).stream().findFirst()
                .orElseThrow(() -> ApplicationException.notFound("Event"));
    }

    /** Events overlapping [from, to). */
    private List<CalendarItem> events(String where, UUID id, Instant from, Instant to) {
        return events(where, new Object[] {id}, from, to);
    }

    private List<CalendarItem> events(String where, Object[] args, Instant from, Instant to) {
        Object[] all = java.util.Arrays.copyOf(args, args.length + 2);
        all[args.length] = Timestamp.from(to);
        all[args.length + 1] = Timestamp.from(from);
        return jdbc.query("SELECT e.*, u.full_name FROM class_events e JOIN users u ON u.id = e.teacher_id WHERE " + where
                        + " AND e.starts_at < ? AND coalesce(e.ends_at, e.starts_at) >= ? ORDER BY e.starts_at",
                (rs, i) -> new CalendarItem(rs.getObject("id", UUID.class), ItemKind.EVENT, rs.getString("title"),
                        rs.getString("description"), rs.getTimestamp("starts_at").toInstant(),
                        rs.getTimestamp("ends_at") == null ? null : rs.getTimestamp("ends_at").toInstant(),
                        rs.getBoolean("all_day"), rs.getString("full_name"), null),
                all);
    }

    private static EventInput validate(EventInput e) {
        if (e == null || e.startsAt() == null) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "An event needs a start");
        }
        if (e.endsAt() != null && e.endsAt().isBefore(e.startsAt())) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "An event cannot end before it starts");
        }
        String description = e.description() == null || e.description().isBlank() ? null : e.description().strip();
        if (description != null && description.length() > 2000) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Keep the description under 2000 characters");
        }
        return new EventInput(required(e.title(), 200, "An event needs a title"), description, e.startsAt(), e.endsAt(),
                e.allDay());
    }

    private static String required(String value, int max, String message) {
        String v = value == null ? "" : value.strip();
        if (v.isEmpty() || v.length() > max) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, message);
        }
        return v;
    }
}
