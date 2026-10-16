package com.sapio.classroom;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.document.Document;
import com.sapio.document.DocumentService;
import com.sapio.document.DocumentStatus;
import com.sapio.notification.NotificationService;
import com.sapio.study.assess.AssessmentDifficulty;
import com.sapio.study.assess.AssessmentService;
import com.sapio.study.question.QuestionType;
import com.sapio.user.User;
import com.sapio.user.UserService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * Tests a teacher sets: drafted by the AI over the whole document (objective, subjective or both), reviewed and
 * edited by the teacher, then sent. Each student receives an ordinary assessment on their copy of the document, so
 * they take it, and it is graded and reported, exactly like their own tests; the teacher reads the results here.
 */
@Service
public class ClassTestService {

    public enum Format { OBJECTIVE, SUBJECTIVE, MIXED }

    public enum Status { DRAFT, SENT }

    public record Question(UUID id, int position, int topicPosition, String topicTitle, QuestionType type,
                           String prompt, List<String> options, Integer correctOption, String referenceAnswer,
                           String explanation) {
    }

    public record QuestionInput(int topicPosition, QuestionType type, String prompt, List<String> options,
                                Integer correctOption, String referenceAnswer, String explanation) {
    }

    public record Summary(UUID id, UUID documentId, String documentTitle, String title, Format format,
                          AssessmentDifficulty difficulty, Status status, int questionCount, Instant dueAt,
                          Instant createdAt, Instant sentAt, int assigned, int completed, Double averageScore) {
    }

    public record Test(Summary summary, List<Question> questions, List<Topic> topics) {
    }

    /** A topic of the document, for choosing one when the teacher adds a question. */
    public record Topic(int position, String title) {
    }

    public enum ResultStatus { NOT_STARTED, IN_PROGRESS, COMPLETED, WITHDRAWN }

    public record StudentResult(UUID studentId, String name, String email, UUID assessmentId, ResultStatus status,
                                int answered, int questionCount, Double score, Instant completedAt) {
    }

    /** The class's average on one topic of this test (from the completed reports). */
    public record TopicResult(String title, double averageScore, int students) {
    }

    public record Results(Summary summary, List<StudentResult> students, Double averageScore, Double lowestScore,
                          Double highestScore, List<TopicResult> topics) {
    }

    private final JdbcTemplate jdbc;
    private final RosterService roster;
    private final ClassShareService shares;
    private final DocumentService documents;
    private final AssessmentService assessments;
    private final NotificationService notifications;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;
    private final UserService users;

    /** "Sat, Oct 18, 11:59 PM", in the student's own time zone. */
    private static final DateTimeFormatter DUE = DateTimeFormatter.ofPattern("EEE, MMM d, h:mm a", Locale.ENGLISH);

    ClassTestService(JdbcTemplate jdbc, RosterService roster, ClassShareService shares, DocumentService documents,
                     AssessmentService assessments, NotificationService notifications, TransactionTemplate tx,
                     JsonMapper json, Clock clock, UserService users) {
        this.users = users;
        this.jdbc = jdbc;
        this.roster = roster;
        this.shares = shares;
        this.documents = documents;
        this.assessments = assessments;
        this.notifications = notifications;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    /** Drafts a test with the AI (one call), saved as a draft for the teacher to review. */
    public Test create(UUID teacherId, UUID documentId, String title, Format format, AssessmentDifficulty difficulty,
                       int questionCount) {
        roster.requireTeacher(teacherId);
        Document document = documents.get(teacherId, documentId);
        if (document.getStatus() != DocumentStatus.READY) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "Create a test once the document is ready");
        }
        List<AssessmentService.DraftQuestion> drafts = assessments.draftQuestions(teacherId, documentId, questionCount,
                difficulty, format.name());
        UUID id = UUID.randomUUID();
        String name = title == null || title.isBlank() ? defaultTitle(document) : title.strip();
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO class_tests (id, teacher_id, document_id, title, format, difficulty, status, "
                            + "created_at) VALUES (?, ?, ?, ?, ?, ?, 'DRAFT', ?)", id, teacherId, documentId,
                    truncate(name, 200), format.name(), difficulty.name(), Timestamp.from(clock.instant()));
            int position = 1;
            for (AssessmentService.DraftQuestion q : drafts) {
                insertQuestion(id, position++, q.topicPosition(), q.topicTitle(), new QuestionInput(q.topicPosition(),
                        q.type(), q.prompt(), q.options(), q.correctOption(), q.referenceAnswer(), q.explanation()),
                        q.sourcePaths());
            }
        });
        return get(teacherId, id);
    }

    public List<Summary> list(UUID teacherId, UUID documentId) {
        roster.requireTeacher(teacherId);
        return jdbc.query(SUMMARY_SQL + " WHERE t.teacher_id = ? AND (CAST(? AS uuid) IS NULL OR t.document_id = ?) "
                + SUMMARY_GROUP + " ORDER BY t.created_at DESC", SUMMARY, teacherId, documentId, documentId);
    }

    public Test get(UUID teacherId, UUID testId) {
        Summary summary = requireTest(teacherId, testId);
        List<Question> questions = jdbc.query("SELECT * FROM class_test_questions WHERE test_id = ? ORDER BY position",
                (rs, i) -> new Question(rs.getObject("id", UUID.class), rs.getInt("position"),
                        rs.getInt("topic_position"), rs.getString("topic_title"),
                        QuestionType.valueOf(rs.getString("type")), rs.getString("prompt"),
                        List.of((String[]) rs.getArray("options").getArray()),
                        (Integer) rs.getObject("correct_option"), rs.getString("reference_answer"),
                        rs.getString("explanation")), testId);
        List<Topic> topics = jdbc.query("""
                        SELECT st.position, st.title FROM study_topics st JOIN study_materials m ON m.id = st.material_id
                        WHERE m.document_id = ? ORDER BY st.position""",
                (rs, i) -> new Topic(rs.getInt(1), rs.getString(2)), summary.documentId());
        return new Test(summary, questions, topics);
    }

    public Test update(UUID teacherId, UUID testId, String title, Instant dueAt) {
        requireTest(teacherId, testId);
        if (title == null || title.isBlank()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "A test needs a title");
        }
        jdbc.update("UPDATE class_tests SET title = ?, due_at = ? WHERE id = ?", truncate(title.strip(), 200),
                dueAt == null ? null : Timestamp.from(dueAt), testId);
        return get(teacherId, testId);
    }

    public Test updateQuestion(UUID teacherId, UUID testId, UUID questionId, QuestionInput input) {
        requireDraft(teacherId, testId);
        QuestionInput q = validate(input);
        int updated = jdbc.update("""
                        UPDATE class_test_questions SET topic_position = ?, topic_title = coalesce(?, topic_title),
                               type = ?, prompt = ?, options = ?, correct_option = ?, reference_answer = ?, explanation = ?
                        WHERE id = ? AND test_id = ?""",
                q.topicPosition(), topicTitle(testId, q.topicPosition()), q.type().name(), q.prompt(),
                array(q.options()), q.correctOption(), q.referenceAnswer(), q.explanation(), questionId, testId);
        if (updated == 0) {
            throw ApplicationException.notFound("Question");
        }
        return get(teacherId, testId);
    }

    public Test addQuestion(UUID teacherId, UUID testId, QuestionInput input) {
        requireDraft(teacherId, testId);
        QuestionInput q = validate(input);
        String topic = topicTitle(testId, q.topicPosition());
        if (topic == null) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Choose a topic of this document");
        }
        Integer last = jdbc.queryForObject("SELECT coalesce(max(position), 0) FROM class_test_questions WHERE test_id = ?",
                Integer.class, testId);
        insertQuestion(testId, (last == null ? 0 : last) + 1, q.topicPosition(), topic, q, List.of());
        return get(teacherId, testId);
    }

    public Test deleteQuestion(UUID teacherId, UUID testId, UUID questionId) {
        requireDraft(teacherId, testId);
        tx.executeWithoutResult(status -> {
            if (jdbc.update("DELETE FROM class_test_questions WHERE id = ? AND test_id = ?", questionId, testId) == 0) {
                throw ApplicationException.notFound("Question");
            }
            // Keep positions 1..n.
            jdbc.update("""
                    UPDATE class_test_questions q SET position = r.n
                    FROM (SELECT id, row_number() OVER (ORDER BY position) AS n FROM class_test_questions WHERE test_id = ?) r
                    WHERE q.id = r.id""", testId);
        });
        return get(teacherId, testId);
    }

    /** Deletes a draft. A sent test stays: students' copies and results depend on it. */
    public void delete(UUID teacherId, UUID testId) {
        requireDraft(teacherId, testId);
        jdbc.update("DELETE FROM class_tests WHERE id = ?", testId);
    }

    /**
     * Sends the test to students (empty: every student). Students without the document get it first. Sending
     * again reaches only students who do not have it yet, so the class can grow.
     *
     * @return how many students received it now
     */
    public int send(UUID teacherId, UUID testId, List<UUID> studentIds, Instant dueAt) {
        User teacher = roster.requireTeacher(teacherId);
        Summary summary = requireTest(teacherId, testId);
        Test test = get(teacherId, testId);
        if (test.questions().isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "Add at least one question first");
        }
        Instant due = dueAt != null ? dueAt : summary.dueAt();
        jdbc.update("UPDATE class_tests SET status = 'SENT', sent_at = coalesce(sent_at, ?), due_at = ? WHERE id = ?",
                Timestamp.from(clock.instant()), due == null ? null : Timestamp.from(due), testId);
        Document document = documents.get(teacherId, summary.documentId());
        List<AssessmentService.AssignedQuestion> questions = assignedQuestions(testId);
        List<UUID> targets = studentIds == null || studentIds.isEmpty() ? roster.students(teacherId) : studentIds;
        int sent = 0;
        for (UUID studentId : targets) {
            roster.requireStudentOf(teacherId, studentId);
            Boolean assigned = jdbc.queryForObject("SELECT EXISTS (SELECT 1 FROM class_test_assignments WHERE test_id = ? "
                    + "AND student_id = ? AND assessment_id IS NOT NULL)", Boolean.class, testId, studentId);
            if (Boolean.TRUE.equals(assigned)) {
                continue;
            }
            shares.ensureCopy(teacher, document, studentId);
            UUID copy = shares.copyOf(document.getId(), studentId).orElseThrow();
            UUID assessment = assessments.createAssigned(studentId, copy, summary.title(), teacher.getFullName(), due,
                    summary.difficulty(), questions);
            jdbc.update("""
                            INSERT INTO class_test_assignments (id, test_id, student_id, assessment_id, assigned_at)
                            VALUES (?, ?, ?, ?, ?)
                            ON CONFLICT (test_id, student_id) DO UPDATE SET assessment_id = excluded.assessment_id,
                                                                            assigned_at = excluded.assigned_at""",
                    UUID.randomUUID(), testId, studentId, assessment, Timestamp.from(clock.instant()));
            notifications.post(studentId, NotificationService.Type.TEST_ASSIGNED,
                    teacher.getFullName() + " set you a test: " + summary.title(),
                    questions.size() + " questions" + (due == null ? "" : ", due " + DUE.withZone(
                            users.getById(studentId).zoneId()).format(due) + ". It is in your calendar."),
                    "/assessments/" + assessment, teacher.getFullName());
            sent++;
        }
        return sent;
    }

    /** Who has it, how far they are, their scores, and the class's average per topic. */
    public Results results(UUID teacherId, UUID testId) {
        Summary summary = requireTest(teacherId, testId);
        List<StudentResult> students = new ArrayList<>();
        Map<String, double[]> topicTotals = new LinkedHashMap<>(); // title -> (sum, count)
        jdbc.query("""
                SELECT u.id, u.full_name, u.email, a.id AS assessment_id, a.status, a.score, a.completed_at,
                       a.question_count, a.report_json,
                       (SELECT count(*) FROM assessment_items i WHERE i.assessment_id = a.id AND i.attempt_id IS NOT NULL)
                           AS answered
                FROM class_test_assignments x JOIN users u ON u.id = x.student_id
                JOIN class_memberships m ON m.teacher_id = ? AND m.student_id = x.student_id AND m.status = 'ACCEPTED'
                LEFT JOIN assessments a ON a.id = x.assessment_id
                WHERE x.test_id = ? ORDER BY u.full_name""", rs -> {
                    UUID assessment = rs.getObject("assessment_id", UUID.class);
                    String state = rs.getString("status");
                    int answered = rs.getInt("answered");
                    ResultStatus status = assessment == null ? ResultStatus.WITHDRAWN
                            : "COMPLETED".equals(state) ? ResultStatus.COMPLETED
                            : answered > 0 ? ResultStatus.IN_PROGRESS : ResultStatus.NOT_STARTED;
                    double score = rs.getDouble("score");
                    Double s = rs.wasNull() ? null : score;
                    students.add(new StudentResult(rs.getObject("id", UUID.class), rs.getString("full_name"),
                            rs.getString("email"), assessment, status, answered, rs.getInt("question_count"), s,
                            rs.getTimestamp("completed_at") == null ? null : rs.getTimestamp("completed_at").toInstant()));
                    String report = rs.getString("report_json");
                    if (report != null) {
                        for (JsonNode t : json.readTree(report).path("topics")) {
                            double[] acc = topicTotals.computeIfAbsent(t.path("title").asString(), k -> new double[2]);
                            acc[0] += t.path("score").asDouble();
                            acc[1]++;
                        }
                    }
                }, teacherId, testId);
        List<Double> scores = students.stream().filter(r -> r.score() != null).map(StudentResult::score).toList();
        List<TopicResult> topics = topicTotals.entrySet().stream()
                .map(e -> new TopicResult(e.getKey(), e.getValue()[0] / e.getValue()[1], (int) e.getValue()[1]))
                .sorted((a, b) -> Double.compare(a.averageScore(), b.averageScore())).toList();
        return new Results(summary, students,
                scores.isEmpty() ? null : scores.stream().mapToDouble(Double::doubleValue).average().orElse(0),
                scores.stream().min(Double::compare).orElse(null), scores.stream().max(Double::compare).orElse(null),
                topics);
    }

    // ------------------------------------------------------------------------------- helpers

    private static final String SUMMARY_SQL = """
            SELECT t.*, coalesce(d.title, d.original_filename) AS document_title,
                   (SELECT count(*) FROM class_test_questions q WHERE q.test_id = t.id) AS question_count,
                   count(x.id) FILTER (WHERE x.assessment_id IS NOT NULL) AS assigned,
                   count(a.id) FILTER (WHERE a.status = 'COMPLETED') AS completed,
                   avg(a.score) FILTER (WHERE a.status = 'COMPLETED') AS average
            FROM class_tests t JOIN documents d ON d.id = t.document_id
            LEFT JOIN class_test_assignments x ON x.test_id = t.id
            LEFT JOIN assessments a ON a.id = x.assessment_id""";
    private static final String SUMMARY_GROUP = " GROUP BY t.id, d.title, d.original_filename";

    private static final RowMapper<Summary> SUMMARY = (rs, i) -> {
        double average = rs.getDouble("average");
        boolean noAverage = rs.wasNull();
        return new Summary(rs.getObject("id", UUID.class), rs.getObject("document_id", UUID.class),
                rs.getString("document_title"), rs.getString("title"), Format.valueOf(rs.getString("format")),
                AssessmentDifficulty.valueOf(rs.getString("difficulty")), Status.valueOf(rs.getString("status")),
                rs.getInt("question_count"), rs.getTimestamp("due_at") == null ? null : rs.getTimestamp("due_at").toInstant(),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("sent_at") == null ? null : rs.getTimestamp("sent_at").toInstant(), rs.getInt("assigned"),
                rs.getInt("completed"), noAverage ? null : average);
    };

    private Summary requireTest(UUID teacherId, UUID testId) {
        roster.requireTeacher(teacherId);
        return jdbc.query(SUMMARY_SQL + " WHERE t.id = ? AND t.teacher_id = ?" + SUMMARY_GROUP, SUMMARY, testId,
                teacherId).stream().findFirst().orElseThrow(() -> ApplicationException.notFound("Test"));
    }

    private void requireDraft(UUID teacherId, UUID testId) {
        if (requireTest(teacherId, testId).status() != Status.DRAFT) {
            throw new ApplicationException(ErrorCode.INVALID_STATE,
                    "This test was sent; its questions can no longer change");
        }
    }

    private List<AssessmentService.AssignedQuestion> assignedQuestions(UUID testId) {
        return jdbc.query("SELECT * FROM class_test_questions WHERE test_id = ? ORDER BY position",
                (rs, i) -> new AssessmentService.AssignedQuestion(rs.getInt("topic_position"),
                        QuestionType.valueOf(rs.getString("type")), rs.getString("prompt"),
                        List.of((String[]) rs.getArray("options").getArray()), (Integer) rs.getObject("correct_option"),
                        rs.getString("reference_answer"), rs.getString("explanation"),
                        List.of((String[]) rs.getArray("source_paths").getArray())), testId);
    }

    private void insertQuestion(UUID testId, int position, int topicPosition, String topicTitle, QuestionInput q,
                                List<String> sourcePaths) {
        jdbc.update("""
                        INSERT INTO class_test_questions (id, test_id, position, topic_position, topic_title, type, prompt,
                                                          options, correct_option, reference_answer, explanation,
                                                          source_paths)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)""",
                UUID.randomUUID(), testId, position, topicPosition, truncate(topicTitle, 400), q.type().name(),
                q.prompt(), array(q.options()), q.correctOption(), q.referenceAnswer(),
                q.explanation() == null ? "" : q.explanation(), array(sourcePaths));
    }

    private String topicTitle(UUID testId, int topicPosition) {
        return jdbc.queryForList("""
                        SELECT st.title FROM class_tests t JOIN study_materials m ON m.document_id = t.document_id
                        JOIN study_topics st ON st.material_id = m.id AND st.position = ? WHERE t.id = ?""",
                String.class, topicPosition, testId).stream().findFirst().orElse(null);
    }

    /** Checks a question the teacher wrote or edited; choice questions need options and a correct one. */
    static QuestionInput validate(QuestionInput q) {
        if (q == null || q.type() == null) {
            throw invalid("Choose a question type");
        }
        String prompt = q.prompt() == null ? "" : q.prompt().strip();
        if (prompt.isEmpty() || prompt.length() > 2000) {
            throw invalid("A question needs its text (up to 2000 characters)");
        }
        String explanation = q.explanation() == null ? "" : q.explanation().strip();
        return switch (q.type()) {
            case SHORT_ANSWER -> {
                String reference = q.referenceAnswer() == null ? "" : q.referenceAnswer().strip();
                if (reference.isEmpty()) {
                    throw invalid("A written-answer question needs a model answer (used for grading)");
                }
                yield new QuestionInput(q.topicPosition(), q.type(), prompt, List.of(), null, reference, explanation);
            }
            case TRUE_FALSE, MULTIPLE_CHOICE -> {
                List<String> options = q.options() == null ? List.of()
                        : q.options().stream().map(o -> o == null ? "" : o.strip()).toList();
                int min = q.type() == QuestionType.TRUE_FALSE ? 2 : 2;
                int max = q.type() == QuestionType.TRUE_FALSE ? 2 : 6;
                if (options.size() < min || options.size() > max || options.stream().anyMatch(String::isEmpty)) {
                    throw invalid(q.type() == QuestionType.TRUE_FALSE ? "True / false needs exactly two options"
                            : "A multiple-choice question needs 2 to 6 options, none empty");
                }
                if (q.correctOption() == null || q.correctOption() < 0 || q.correctOption() >= options.size()) {
                    throw invalid("Mark the correct option");
                }
                yield new QuestionInput(q.topicPosition(), q.type(), prompt, options, q.correctOption(),
                        options.get(q.correctOption()), explanation);
            }
        };
    }

    private static ApplicationException invalid(String message) {
        return new ApplicationException(ErrorCode.VALIDATION_FAILED, message);
    }

    private String[] array(List<String> values) {
        return values == null ? new String[0] : values.toArray(String[]::new);
    }

    private static String defaultTitle(Document d) {
        return "Test: " + (d.getTitle() == null ? d.getOriginalFilename() : d.getTitle());
    }

    private static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
