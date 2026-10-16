package com.sapio.classroom;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.document.Document;
import com.sapio.document.DocumentService;
import com.sapio.document.DocumentStatus;
import com.sapio.document.copy.DocumentCloner;
import com.sapio.notification.NotificationService;
import com.sapio.user.User;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Documents a teacher shares with their students. Each student gets an independent copy in their library (their
 * notes, lessons and progress stay theirs); a class share links the copy to the teacher's document, which is what
 * the teacher's numbers (opened, progress, tests) are computed from.
 */
@Service
public class ClassShareService {

    private static final Logger log = LoggerFactory.getLogger(ClassShareService.class);

    public record Share(UUID id, UUID studentId, String studentName, String studentEmail, UUID copyDocumentId,
                        Instant sharedAt, Instant firstOpenedAt, Instant lastOpenedAt) {
    }

    public record ShareResult(int shared, int alreadyHad) {
    }

    /** What the teacher's library card shows for one of their documents. */
    public record DocumentStats(UUID documentId, int sharedWith, int opened, int testsCompleted, Double averageScore) {
    }

    private final JdbcTemplate jdbc;
    private final RosterService roster;
    private final DocumentService documents;
    private final DocumentCloner cloner;
    private final NotificationService notifications;
    private final Clock clock;

    ClassShareService(JdbcTemplate jdbc, RosterService roster, DocumentService documents, DocumentCloner cloner,
                      NotificationService notifications, Clock clock) {
        this.jdbc = jdbc;
        this.roster = roster;
        this.documents = documents;
        this.cloner = cloner;
        this.notifications = notifications;
        this.clock = clock;
    }

    /** Shares a ready document with the given students (empty: every student). Already shared: left as is. */
    public ShareResult share(UUID teacherId, UUID documentId, List<UUID> studentIds) {
        User teacher = roster.requireTeacher(teacherId);
        Document document = documents.get(teacherId, documentId);
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "Share a document once it is ready");
        }
        List<UUID> targets = studentIds == null || studentIds.isEmpty() ? roster.students(teacherId) : studentIds;
        int shared = 0;
        int had = 0;
        for (UUID studentId : targets) {
            roster.requireStudentOf(teacherId, studentId);
            if (ensureCopy(teacher, document, studentId).isPresent()) {
                shared++;
            } else {
                had++;
            }
        }
        return new ShareResult(shared, had);
    }

    /**
     * The student's copy of the teacher's document, made now if they have none (never shared, or deleted).
     *
     * @return the new copy's id, or empty when the student already had one
     */
    Optional<UUID> ensureCopy(User teacher, Document document, UUID studentId) {
        if (copyOf(document.getId(), studentId).isPresent()) {
            return Optional.empty();
        }
        UUID copy = cloner.copy(document.getId(), studentId,
                new DocumentCloner.Origin(null, teacher.getFullName(), DocumentCloner.Origin.Kind.CLASS));
        Timestamp now = Timestamp.from(clock.instant());
        int updated = jdbc.update("UPDATE class_shares SET copy_document_id = ?, shared_at = ?, first_opened_at = NULL, "
                + "last_opened_at = NULL WHERE source_document_id = ? AND student_id = ?", copy, now, document.getId(),
                studentId);
        if (updated == 0) {
            jdbc.update("INSERT INTO class_shares (id, teacher_id, source_document_id, student_id, copy_document_id, "
                    + "shared_at) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), teacher.getId(), document.getId(),
                    studentId, copy, now);
        }
        String title = document.getTitle() == null ? document.getOriginalFilename() : document.getTitle();
        notifications.post(studentId, NotificationService.Type.DOCUMENT_SHARED,
                teacher.getFullName() + " shared “" + title + "” with you", "It is in your library now.",
                "/documents/" + copy, teacher.getFullName());
        log.info("Document {} shared with student {} as {}", document.getId(), studentId, copy);
        return Optional.of(copy);
    }

    /** The student's current copy of a teacher's document. */
    public Optional<UUID> copyOf(UUID sourceDocumentId, UUID studentId) {
        return jdbc.queryForList("SELECT copy_document_id FROM class_shares WHERE source_document_id = ? AND "
                + "student_id = ? AND copy_document_id IS NOT NULL", UUID.class, sourceDocumentId, studentId)
                .stream().findFirst();
    }

    /** Who a document was shared with (current students only). */
    public List<Share> shares(UUID teacherId, UUID documentId) {
        roster.requireTeacher(teacherId);
        documents.get(teacherId, documentId);
        return jdbc.query("""
                        SELECT s.*, u.full_name, u.email FROM class_shares s JOIN users u ON u.id = s.student_id
                        JOIN class_memberships m ON m.teacher_id = s.teacher_id AND m.student_id = s.student_id
                                                 AND m.status = 'ACCEPTED'
                        WHERE s.teacher_id = ? AND s.source_document_id = ? ORDER BY u.full_name""",
                (rs, i) -> new Share(rs.getObject("id", UUID.class), rs.getObject("student_id", UUID.class),
                        rs.getString("full_name"), rs.getString("email"), rs.getObject("copy_document_id", UUID.class),
                        rs.getTimestamp("shared_at").toInstant(), instant(rs.getTimestamp("first_opened_at")),
                        instant(rs.getTimestamp("last_opened_at"))),
                teacherId, documentId);
    }

    /** Every class share of a student from this teacher: (source document, copy). */
    public Map<UUID, UUID> copiesOfStudent(UUID teacherId, UUID studentId) {
        Map<UUID, UUID> copies = new HashMap<>();
        jdbc.query("SELECT source_document_id, copy_document_id FROM class_shares WHERE teacher_id = ? AND student_id = ? "
                + "AND copy_document_id IS NOT NULL", rs -> {
                    copies.put(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class));
                }, teacherId, studentId);
        return copies;
    }

    /** Whether this document is a copy the teacher shared with one of their current students. */
    public boolean isSharedCopy(UUID teacherId, UUID copyDocumentId) {
        Boolean shared = jdbc.queryForObject("""
                SELECT EXISTS (SELECT 1 FROM class_shares s JOIN class_memberships m
                               ON m.teacher_id = s.teacher_id AND m.student_id = s.student_id AND m.status = 'ACCEPTED'
                               WHERE s.teacher_id = ? AND s.copy_document_id = ?)""", Boolean.class, teacherId,
                copyDocumentId);
        return Boolean.TRUE.equals(shared);
    }

    /** The student opened one of their documents: if a teacher shared it, the teacher sees it as opened. */
    public void opened(UUID studentId, UUID documentId) {
        Timestamp now = Timestamp.from(clock.instant());
        jdbc.update("UPDATE class_shares SET first_opened_at = coalesce(first_opened_at, ?), last_opened_at = ? "
                + "WHERE student_id = ? AND copy_document_id = ?", now, now, studentId, documentId);
    }

    /** Shared with / opened by / tests completed / average score, for each of the teacher's documents. */
    public List<DocumentStats> stats(UUID teacherId) {
        roster.requireTeacher(teacherId);
        List<DocumentStats> out = new ArrayList<>();
        jdbc.query("""
                SELECT s.source_document_id,
                       count(DISTINCT s.student_id) AS shared_with,
                       count(DISTINCT s.student_id) FILTER (WHERE s.first_opened_at IS NOT NULL) AS opened,
                       count(a.id) AS tests,
                       avg(a.score) AS average
                FROM class_shares s
                JOIN class_memberships m ON m.teacher_id = s.teacher_id AND m.student_id = s.student_id
                                         AND m.status = 'ACCEPTED'
                -- Only the teacher's class tests on this document; students' own tests are not counted.
                LEFT JOIN class_tests ct ON ct.document_id = s.source_document_id AND ct.teacher_id = s.teacher_id
                LEFT JOIN class_test_assignments x ON x.test_id = ct.id AND x.student_id = s.student_id
                LEFT JOIN assessments a ON a.id = x.assessment_id AND a.status = 'COMPLETED'
                WHERE s.teacher_id = ? AND s.copy_document_id IS NOT NULL
                GROUP BY s.source_document_id""", rs -> {
                    double avg = rs.getDouble("average");
                    out.add(new DocumentStats(rs.getObject("source_document_id", UUID.class), rs.getInt("shared_with"),
                            rs.getInt("opened"), rs.getInt("tests"), rs.wasNull() ? null : avg));
                }, teacherId);
        return out;
    }

    private static Instant instant(Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
