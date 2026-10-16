package com.sapio.classroom;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.study.assess.AssessmentResponse;
import com.sapio.study.assess.AssessmentService;
import com.sapio.study.assess.AssessmentStatus;
import com.sapio.study.assess.AssessmentSummary;
import com.sapio.study.learn.TopicStatus;
import com.sapio.study.material.MaterialResponse;
import com.sapio.study.overview.StudyOverviewService;
import com.sapio.user.User;
import com.sapio.user.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * What a teacher learns about their students, always limited to the documents the teacher shared with them (their
 * own uploads stay private): progress per document, test results, and the topics each one should work on.
 */
@Service
public class InsightsService {

    /** Below this mastery or test score a topic counts as one to work on. */
    static final double WEAK = 0.5;

    public record TopicProgress(int position, String title, TopicStatus status, Double mastery) {
    }

    public record TestResult(UUID assessmentId, String title, boolean assigned, AssessmentStatus status, Double score,
                             Instant createdAt, Instant completedAt) {
    }

    /** One student on one document the teacher shared. {@code topics} is empty when the copy was deleted. */
    public record DocumentProgress(UUID sourceDocumentId, String documentTitle, UUID studentId, String studentName,
                                   String studentEmail, boolean removedByStudent, Instant sharedAt,
                                   Instant firstOpenedAt, Instant lastOpenedAt, int topicCount, int completedTopics,
                                   Double averageMastery, List<TopicProgress> topics, List<TestResult> tests,
                                   Double averageScore, List<String> weakTopics) {
    }

    /** A topic a student should work on, and why. */
    public record WeakTopic(String documentTitle, String topic, Double score, String reason) {
    }

    public record StudentProfile(UUID id, String name, String email, Instant joinedAt, List<DocumentProgress> documents,
                                 List<WeakTopic> needsWork, Double averageScore, int testsCompleted) {
    }

    public record RecentResult(UUID assessmentId, UUID studentId, String studentName, String documentTitle,
                               String testTitle, double score, Instant completedAt) {
    }

    public record Attention(UUID studentId, String name, String reason, List<String> topics) {
    }

    public record Overview(int students, int pendingInvites, int documentsShared, int testsSent, int testsCompleted,
                           Double averageScore, Double lowestScore, Double highestScore, List<RecentResult> recent,
                           List<Attention> needsAttention) {
    }

    private final JdbcTemplate jdbc;
    private final RosterService roster;
    private final ClassShareService shares;
    private final StudyOverviewService overview;
    private final AssessmentService assessments;
    private final UserService users;
    private final JsonMapper json;

    InsightsService(JdbcTemplate jdbc, RosterService roster, ClassShareService shares, StudyOverviewService overview,
                    AssessmentService assessments, UserService users, JsonMapper json) {
        this.jdbc = jdbc;
        this.roster = roster;
        this.shares = shares;
        this.overview = overview;
        this.assessments = assessments;
        this.users = users;
        this.json = json;
    }

    /** Every current student's progress on one of the teacher's documents. */
    public List<DocumentProgress> documentStudents(UUID teacherId, UUID documentId) {
        String title = documentTitle(documentId);
        return shares.shares(teacherId, documentId).stream()
                .map(s -> progress(documentId, title, s.studentId(), s.studentName(), s.studentEmail(),
                        s.copyDocumentId(), s.sharedAt(), s.firstOpenedAt(), s.lastOpenedAt()))
                .toList();
    }

    public StudentProfile student(UUID teacherId, UUID studentId) {
        roster.requireTeacher(teacherId);
        roster.requireStudentOf(teacherId, studentId);
        User student = users.getById(studentId);
        Instant joined = instant(jdbc.queryForObject("SELECT responded_at FROM class_memberships WHERE teacher_id = ? "
                + "AND student_id = ?", java.sql.Timestamp.class, teacherId, studentId));
        List<DocumentProgress> docs = new ArrayList<>();
        jdbc.query("""
                SELECT s.source_document_id, s.copy_document_id, s.shared_at, s.first_opened_at, s.last_opened_at
                FROM class_shares s WHERE s.teacher_id = ? AND s.student_id = ? ORDER BY s.shared_at DESC""", rs -> {
                    UUID source = rs.getObject(1, UUID.class);
                    docs.add(progress(source, documentTitle(source), studentId, student.getFullName(),
                            student.getEmail(), rs.getObject(2, UUID.class), rs.getTimestamp(3).toInstant(),
                            instant(rs.getTimestamp(4)), instant(rs.getTimestamp(5))));
                }, teacherId, studentId);
        List<WeakTopic> weak = new ArrayList<>();
        for (DocumentProgress d : docs) {
            for (TopicProgress t : d.topics()) {
                if (t.mastery() != null && t.mastery() < WEAK) {
                    weak.add(new WeakTopic(d.documentTitle(), t.title(), t.mastery(), "low accuracy in practice"));
                }
            }
            for (TestResult r : d.tests()) {
                if (r.status() == AssessmentStatus.COMPLETED) {
                    weakAreasOf(r.assessmentId()).forEach(topic -> weak.add(new WeakTopic(d.documentTitle(), topic,
                            null, "weak in “" + r.title() + "”")));
                }
            }
        }
        List<WeakTopic> needsWork = dedupe(weak);
        List<Double> scores = docs.stream().flatMap(d -> d.tests().stream()).map(TestResult::score)
                .filter(Objects::nonNull).toList();
        return new StudentProfile(studentId, student.getFullName(), student.getEmail(), joined, docs, needsWork,
                scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                scores.size());
    }

    /** A student's test (with its report), for their teacher: only on a document the teacher shared. */
    public AssessmentResponse assessment(UUID teacherId, UUID assessmentId) {
        roster.requireTeacher(teacherId);
        List<Map<String, Object>> owner = jdbc.queryForList("""
                SELECT a.user_id, m.document_id FROM assessments a JOIN study_materials m ON m.id = a.material_id
                WHERE a.id = ?""", assessmentId);
        if (owner.isEmpty()) {
            throw ApplicationException.notFound("Test");
        }
        UUID studentId = (UUID) owner.getFirst().get("user_id");
        UUID documentId = (UUID) owner.getFirst().get("document_id");
        if (!shares.isSharedCopy(teacherId, documentId)) {
            throw ApplicationException.notFound("Test");
        }
        return assessments.get(studentId, assessmentId);
    }

    /** The teacher's home: the class at a glance. */
    public Overview overview(UUID teacherId) {
        roster.requireTeacher(teacherId);
        List<RosterService.Member> members = roster.roster(teacherId);
        int students = (int) members.stream().filter(m -> m.status() == RosterService.Status.ACCEPTED).count();
        int pending = (int) members.stream().filter(m -> m.status() == RosterService.Status.PENDING).count();
        Integer documents = jdbc.queryForObject("SELECT count(DISTINCT source_document_id) FROM class_shares WHERE "
                + "teacher_id = ?", Integer.class, teacherId);
        Integer tests = jdbc.queryForObject("SELECT count(*) FROM class_tests WHERE teacher_id = ? AND status = 'SENT'",
                Integer.class, teacherId);

        // Every completed test on a document the teacher shared: their own tests and the teacher's.
        List<RecentResult> completed = jdbc.query("""
                        SELECT a.id, a.user_id, u.full_name, coalesce(d.title, d.original_filename) AS document_title,
                               a.title, a.score, a.completed_at
                        FROM class_shares s
                        JOIN class_memberships m ON m.teacher_id = s.teacher_id AND m.student_id = s.student_id
                                                 AND m.status = 'ACCEPTED'
                        JOIN study_materials sm ON sm.document_id = s.copy_document_id
                        JOIN assessments a ON a.material_id = sm.id AND a.user_id = s.student_id AND a.status = 'COMPLETED'
                        JOIN users u ON u.id = a.user_id
                        JOIN documents d ON d.id = s.source_document_id
                        WHERE s.teacher_id = ? ORDER BY a.completed_at DESC""",
                (rs, i) -> new RecentResult(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class), rs.getString(3),
                        rs.getString(4), rs.getString(5), rs.getDouble(6), rs.getTimestamp(7).toInstant()),
                teacherId);
        Double average = completed.isEmpty() ? null
                : completed.stream().mapToDouble(RecentResult::score).average().orElse(0);
        Double lowest = completed.stream().map(RecentResult::score).min(Double::compare).orElse(null);
        Double highest = completed.stream().map(RecentResult::score).max(Double::compare).orElse(null);

        // Needs attention: a low average across their tests, with the topics their reports flagged.
        Map<UUID, List<RecentResult>> byStudent = new LinkedHashMap<>();
        completed.forEach(r -> byStudent.computeIfAbsent(r.studentId(), k -> new ArrayList<>()).add(r));
        List<Attention> attention = new ArrayList<>();
        byStudent.forEach((student, results) -> {
            double avg = results.stream().mapToDouble(RecentResult::score).average().orElse(1);
            if (avg < WEAK) {
                List<String> topics = weakAreasOf(results.getFirst().assessmentId());
                attention.add(new Attention(student, results.getFirst().studentName(),
                        "averages " + Math.round(avg * 100) + "% over " + results.size()
                                + (results.size() == 1 ? " test" : " tests"), topics));
            }
        });
        attention.sort(Comparator.comparing(Attention::name));
        return new Overview(students, pending, documents == null ? 0 : documents, tests == null ? 0 : tests,
                completed.size(), average, lowest, highest, completed.stream().limit(8).toList(), attention);
    }

    // ------------------------------------------------------------------------------- helpers

    private DocumentProgress progress(UUID sourceId, String title, UUID studentId, String name, String email,
                                      UUID copyId, Instant sharedAt, Instant firstOpened, Instant lastOpened) {
        if (copyId == null) {
            return new DocumentProgress(sourceId, title, studentId, name, email, true, sharedAt, firstOpened, lastOpened,
                    0, 0, null, List.of(), List.of(), null, List.of());
        }
        MaterialResponse material;
        try {
            material = overview.material(studentId, copyId);
        } catch (ApplicationException e) {
            return new DocumentProgress(sourceId, title, studentId, name, email, false, sharedAt, firstOpened,
                    lastOpened, 0, 0, null, List.of(), List.of(), null, List.of());
        }
        List<TopicProgress> topics = material.topics().stream()
                .map(t -> new TopicProgress(t.position(), t.title(), t.status(), t.mastery())).toList();
        List<Double> masteries = topics.stream().map(TopicProgress::mastery).filter(Objects::nonNull).toList();
        List<TestResult> tests = assessments.list(studentId, copyId).stream().map(InsightsService::result).toList();
        List<Double> scores = tests.stream().map(TestResult::score).filter(Objects::nonNull).toList();
        List<String> weak = topics.stream().filter(t -> t.mastery() != null && t.mastery() < WEAK)
                .map(TopicProgress::title).toList();
        return new DocumentProgress(sourceId, title, studentId, name, email, false, sharedAt, firstOpened, lastOpened,
                material.stats().topicCount(), material.stats().completedTopics(),
                masteries.isEmpty() ? null : masteries.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                topics, tests, scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average()
                .orElse(0), weak);
    }

    private static TestResult result(AssessmentSummary a) {
        boolean assigned = a.assignment() != null;
        return new TestResult(a.id(), assigned && a.assignment().title() != null ? a.assignment().title() : "Practice test",
                assigned, a.status(), a.score(), a.createdAt(), a.completedAt());
    }

    /** Topics a completed test's report rated weak. */
    private List<String> weakAreasOf(UUID assessmentId) {
        List<String> reports = jdbc.queryForList("SELECT report_json FROM assessments WHERE id = ?", String.class,
                assessmentId);
        if (reports.isEmpty() || reports.getFirst() == null) {
            return List.of();
        }
        List<String> topics = new ArrayList<>();
        for (JsonNode t : json.readTree(reports.getFirst()).path("topics")) {
            if ("WEAK".equals(t.path("level").asString())) {
                topics.add(t.path("title").asString());
            }
        }
        return topics;
    }

    private static List<WeakTopic> dedupe(List<WeakTopic> weak) {
        Map<String, WeakTopic> unique = new LinkedHashMap<>();
        for (WeakTopic w : weak) {
            unique.merge(w.documentTitle() + "\u0000" + w.topic(), w, (a, b) -> a.score() != null ? a : b);
        }
        return new ArrayList<>(unique.values());
    }

    private String documentTitle(UUID documentId) {
        return jdbc.queryForList("SELECT coalesce(title, original_filename) FROM documents WHERE id = ?", String.class,
                documentId).stream().findFirst().orElse("Document");
    }

    private static Instant instant(java.sql.Timestamp t) {
        return t == null ? null : t.toInstant();
    }
}
