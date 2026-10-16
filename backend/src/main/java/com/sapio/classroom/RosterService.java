package com.sapio.classroom;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.notification.NotificationService;
import com.sapio.user.Role;
import com.sapio.user.User;
import com.sapio.user.UserRepository;
import com.sapio.user.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * A teacher's students. A teacher invites by email; the person sees the invitation in their notifications (now, or
 * once they sign up with that address) and accepts or declines. Only accepted students receive documents, tests,
 * notices and events, and only they can be looked at by the teacher.
 */
@Service
public class RosterService {

    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");
    static final int MAX_INVITES = 100;

    public enum Status { PENDING, ACCEPTED, DECLINED }

    public enum InviteOutcome { INVITED, ALREADY_INVITED, ALREADY_A_STUDENT, NOT_A_STUDENT_ACCOUNT, INVALID_EMAIL, YOURSELF }

    public record InviteResult(String email, InviteOutcome outcome) {
    }

    /** A row of the teacher's roster; {@code studentId} / {@code name} once the student has answered. */
    public record Member(UUID id, UUID studentId, String email, String name, Status status, Instant invitedAt,
                         Instant respondedAt) {
    }

    public record Invitation(UUID id, String teacherName, String teacherEmail, Instant invitedAt) {
    }

    public record Teacher(UUID id, String name, String email) {
    }

    private static final RowMapper<Member> MEMBER = (rs, i) -> new Member(rs.getObject("id", UUID.class),
            rs.getObject("student_id", UUID.class), rs.getString("student_email"), rs.getString("full_name"),
            Status.valueOf(rs.getString("status")), rs.getTimestamp("invited_at").toInstant(),
            rs.getTimestamp("responded_at") == null ? null : rs.getTimestamp("responded_at").toInstant());

    private final JdbcTemplate jdbc;
    private final UserService users;
    private final UserRepository userRepository;
    private final NotificationService notifications;
    private final Clock clock;

    RosterService(JdbcTemplate jdbc, UserService users, UserRepository userRepository,
                  NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.users = users;
        this.userRepository = userRepository;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** The user, who must be a teacher. */
    public User requireTeacher(UUID userId) {
        User user = users.getById(userId);
        if (user.getRole() != Role.TEACHER) {
            throw new ApplicationException(ErrorCode.ACCESS_DENIED, "Only teachers can do this");
        }
        return user;
    }

    /** The student, who must have accepted this teacher's invitation (otherwise: not found, so nothing leaks). */
    public void requireStudentOf(UUID teacherId, UUID studentId) {
        if (!isStudentOf(teacherId, studentId)) {
            throw ApplicationException.notFound("Student");
        }
    }

    public boolean isStudentOf(UUID teacherId, UUID studentId) {
        Boolean member = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM class_memberships WHERE teacher_id = ? "
                + "AND student_id = ? AND status = 'ACCEPTED')", Boolean.class, teacherId, studentId);
        return Boolean.TRUE.equals(member);
    }

    @Transactional
    public List<InviteResult> invite(UUID teacherId, List<String> emails) {
        User teacher = requireTeacher(teacherId);
        if (emails.size() > MAX_INVITES) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Invite at most " + MAX_INVITES + " at once");
        }
        Set<String> unique = new LinkedHashSet<>();
        emails.stream().map(e -> e == null ? "" : e.strip()).filter(e -> !e.isEmpty()).forEach(unique::add);
        Instant now = clock.instant();
        List<InviteResult> results = new ArrayList<>();
        for (String raw : unique) {
            String email = User.normalizeEmail(raw);
            if (!EMAIL.matcher(email).matches()) {
                results.add(new InviteResult(raw, InviteOutcome.INVALID_EMAIL));
                continue;
            }
            if (email.equals(teacher.getEmail())) {
                results.add(new InviteResult(raw, InviteOutcome.YOURSELF));
                continue;
            }
            Optional<User> account = userRepository.findByEmail(email);
            if (account.isPresent() && account.get().getRole() != Role.STUDENT) {
                results.add(new InviteResult(raw, InviteOutcome.NOT_A_STUDENT_ACCOUNT));
                continue;
            }
            List<String> existing = jdbc.queryForList("SELECT status FROM class_memberships WHERE teacher_id = ? "
                    + "AND student_email = ?", String.class, teacherId, email);
            if (!existing.isEmpty() && !existing.getFirst().equals(Status.DECLINED.name())) {
                results.add(new InviteResult(raw, existing.getFirst().equals(Status.ACCEPTED.name())
                        ? InviteOutcome.ALREADY_A_STUDENT : InviteOutcome.ALREADY_INVITED));
                continue;
            }
            if (existing.isEmpty()) {
                jdbc.update("INSERT INTO class_memberships (id, teacher_id, student_email, status, invited_at) "
                        + "VALUES (?, ?, ?, 'PENDING', ?)", UUID.randomUUID(), teacherId, email, Timestamp.from(now));
            } else { // declined before: ask again
                jdbc.update("UPDATE class_memberships SET status = 'PENDING', invited_at = ?, responded_at = NULL "
                        + "WHERE teacher_id = ? AND student_email = ?", Timestamp.from(now), teacherId, email);
            }
            account.ifPresent(student -> notifications.post(student.getId(), NotificationService.Type.CLASS_INVITE,
                    teacher.getFullName() + " invited you to their class",
                    "Accept to receive their documents, tests, notices and calendar.", null, teacher.getFullName()));
            results.add(new InviteResult(raw, InviteOutcome.INVITED));
        }
        return results;
    }

    public List<Member> roster(UUID teacherId) {
        requireTeacher(teacherId);
        return jdbc.query("""
                SELECT m.*, u.full_name FROM class_memberships m LEFT JOIN users u ON u.id = m.student_id
                WHERE m.teacher_id = ? ORDER BY m.status, coalesce(u.full_name, m.student_email)""", MEMBER, teacherId);
    }

    /** Accepted students' ids. */
    public List<UUID> students(UUID teacherId) {
        return jdbc.queryForList("SELECT student_id FROM class_memberships WHERE teacher_id = ? AND status = "
                + "'ACCEPTED'", UUID.class, teacherId);
    }

    /** Removes a student or withdraws an invitation. What the student already received stays theirs. */
    @Transactional
    public void remove(UUID teacherId, UUID membershipId) {
        requireTeacher(teacherId);
        if (jdbc.update("DELETE FROM class_memberships WHERE id = ? AND teacher_id = ?", membershipId, teacherId) == 0) {
            throw ApplicationException.notFound("Student");
        }
    }

    // ------------------------------------------------------------------------------- the student's side

    public List<Invitation> invitations(UUID studentId) {
        User student = users.getById(studentId);
        return jdbc.query("""
                        SELECT m.id, t.full_name, t.email, m.invited_at FROM class_memberships m
                        JOIN users t ON t.id = m.teacher_id
                        WHERE m.student_email = ? AND m.status = 'PENDING' ORDER BY m.invited_at DESC""",
                (rs, i) -> new Invitation(rs.getObject("id", UUID.class), rs.getString("full_name"),
                        rs.getString("email"), rs.getTimestamp("invited_at").toInstant()), student.getEmail());
    }

    @Transactional
    public void respond(UUID studentId, UUID invitationId, boolean accept) {
        User student = users.getById(studentId);
        List<UUID> teacher = jdbc.queryForList("SELECT teacher_id FROM class_memberships WHERE id = ? AND "
                + "student_email = ? AND status = 'PENDING'", UUID.class, invitationId, student.getEmail());
        if (teacher.isEmpty()) {
            throw ApplicationException.notFound("Invitation");
        }
        jdbc.update("UPDATE class_memberships SET status = ?, student_id = ?, responded_at = ? WHERE id = ?",
                (accept ? Status.ACCEPTED : Status.DECLINED).name(), studentId, Timestamp.from(clock.instant()),
                invitationId);
        notifications.post(teacher.getFirst(), NotificationService.Type.INVITE_ANSWERED,
                student.getFullName() + (accept ? " joined your class" : " declined your invitation"), null,
                accept ? "/students" : null, student.getFullName());
    }

    public List<Teacher> teachers(UUID studentId) {
        return jdbc.query("""
                        SELECT t.id, t.full_name, t.email FROM class_memberships m JOIN users t ON t.id = m.teacher_id
                        WHERE m.student_id = ? AND m.status = 'ACCEPTED' ORDER BY t.full_name""",
                (rs, i) -> new Teacher(rs.getObject("id", UUID.class), rs.getString("full_name"), rs.getString("email")),
                studentId);
    }

    /** The student leaves a teacher's class. */
    @Transactional
    public void leave(UUID studentId, UUID teacherId) {
        jdbc.update("DELETE FROM class_memberships WHERE student_id = ? AND teacher_id = ?", studentId, teacherId);
    }
}
