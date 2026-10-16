package com.sapio.study.assess;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.knowledge.store.KnowledgeStore;
import com.sapio.study.LearningActivityEvent;
import com.sapio.study.ai.Personalization;
import com.sapio.study.ai.StudyAi;
import com.sapio.study.material.MaterialAccess;
import com.sapio.study.material.StudyMaterial;
import com.sapio.study.material.StudyTopic;
import com.sapio.study.material.StudyTopicRepository;
import com.sapio.study.question.AnswerGrader;
import com.sapio.study.question.AnswerRecorder;
import com.sapio.study.question.AnswerRequest;
import com.sapio.study.question.AnswerResult;
import com.sapio.study.question.AttemptContext;
import com.sapio.study.question.QuestionAttempt;
import com.sapio.study.question.QuestionAttemptRepository;
import com.sapio.study.question.QuestionOrigin;
import com.sapio.study.question.QuestionType;
import com.sapio.study.question.QuestionView;
import com.sapio.study.question.ReviewCard;
import com.sapio.study.question.ReviewCardRepository;
import com.sapio.study.question.StudyQuestion;
import com.sapio.study.question.StudyQuestionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * Test mode: generate an interleaved assessment, grade each answer as it is saved (revealing nothing while the test
 * is open), then build the report, which is where results and explanations are shown.
 */
@Service
public class AssessmentService {

    private static final int MAX_AVOID_PROMPTS = 60;

    private final MaterialAccess access;
    private final StudyAi ai;
    private final AssessmentRepository assessments;
    private final AssessmentItemRepository items;
    private final StudyTopicRepository topics;
    private final StudyQuestionRepository questions;
    private final QuestionAttemptRepository attempts;
    private final ReviewCardRepository cards;
    private final AnswerGrader grader;
    private final AnswerRecorder recorder;
    private final ReportBuilder reports;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;
    private final Personalization personalization;
    private final KnowledgeStore store;

    AssessmentService(MaterialAccess access, StudyAi ai, AssessmentRepository assessments,
                      AssessmentItemRepository items, StudyTopicRepository topics, StudyQuestionRepository questions,
                      QuestionAttemptRepository attempts, ReviewCardRepository cards, AnswerGrader grader,
                      AnswerRecorder recorder, ReportBuilder reports, ApplicationEventPublisher events,
                      TransactionTemplate tx, JsonMapper json, Clock clock, Personalization personalization,
                      KnowledgeStore store) {
        this.store = store;
        this.personalization = personalization;
        this.access = access;
        this.ai = ai;
        this.assessments = assessments;
        this.items = items;
        this.topics = topics;
        this.questions = questions;
        this.attempts = attempts;
        this.cards = cards;
        this.grader = grader;
        this.recorder = recorder;
        this.reports = reports;
        this.events = events;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    public AssessmentResponse create(UUID userId, UUID documentId, CreateAssessmentRequest request) {
        StudyMaterial material = access.requireByDocument(userId, documentId);
        List<StudyTopic> all = topics.findByMaterialIdOrderByPosition(material.getId());
        List<StudyTopic> selected = request.topicIds() == null || request.topicIds().isEmpty() ? all
                : all.stream().filter(t -> request.topicIds().contains(t.getId())).toList();
        if (selected.isEmpty()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Select at least one topic of this material");
        }
        Generated generated = generate(userId, material, selected, request.questionCount(), request.difficulty(), "MIXED");
        List<StudyAi.QuestionDraft> usable = generated.questions();
        Map<String, StudyTopic> byKey = generated.byKey();
        Map<String, UUID> sectionNodes = generated.sectionNodes();
        UUID id = tx.execute(status -> {
            Instant now = clock.instant();
            List<StudyAi.QuestionDraft> ordered = usable;
            Assessment assessment = assessments.save(new Assessment(userId, material.getId(), request.difficulty(),
                    ordered.size(), now));
            int position = 1;
            for (StudyAi.QuestionDraft q : ordered) {
                StudyTopic topic = byKey.get(q.topicKey());
                StudyQuestion saved = questions.save(new StudyQuestion(material.getId(), topic.getId(), null,
                        QuestionOrigin.ASSESSMENT, null, position, q.type(), q.prompt(),
                        q.options() == null ? List.of() : q.options(), q.correctOptionIndex(), q.referenceAnswer(),
                        q.explanation() == null ? "" : q.explanation(),
                        q.sourceSectionIds() == null ? List.of() : q.sourceSectionIds().stream()
                                .map(sectionNodes::get).filter(Objects::nonNull).distinct().toList(), now));
                items.save(new AssessmentItem(assessment.getId(), position++, saved.getId()));
            }
            events.publishEvent(LearningActivityEvent.of(userId, LearningActivityEvent.Type.ASSESSMENT_STARTED, now)
                    .document(material.getDocumentId(), material.getTitle()).assessment(assessment.getId()).build());
            return assessment.getId();
        });
        return get(userId, id);
    }

    private record Generated(List<StudyAi.QuestionDraft> questions, Map<String, StudyTopic> byKey,
                             Map<String, UUID> sectionNodes) {
    }

    /** One AI call for {@code count} questions over the topics; only questions on a given topic are kept. */
    private Generated generate(UUID userId, StudyMaterial material, List<StudyTopic> selected, int count,
                               AssessmentDifficulty difficulty, String format) {
        List<String> avoid = questions.findAssessedPrompts(userId, material.getId());
        Map<String, UUID> sectionNodes = new HashMap<>(); // citation id ("§2.3") -> tree node
        List<StudyAi.QuestionTopic> topicSpecs = selected.stream().map(t -> {
            var sources = access.sources(material, t, access.budgets().questions());
            sources.sections().forEach(sec -> sectionNodes.put(sec.id(), sec.nodeId()));
            return new StudyAi.QuestionTopic(t.getId().toString(), t.getTitle(), t.getSummary(), t.getKeyConcepts(),
                    t.getDifficulty(), StudyAi.sources(sources.sections()));
        }).toList();
        StudyAi.QuestionsDraft draft = ai.generateQuestions(new StudyAi.QuestionSpec(material.getTitle(),
                material.getLanguage(), material.getKind(), count, difficulty.name(), format,
                topicSpecs, avoid.subList(Math.max(0, avoid.size() - MAX_AVOID_PROMPTS), avoid.size()),
                personalization.forLearner(material.getOwnerId()).learner()));
        Map<String, StudyTopic> byKey = selected.stream()
                .collect(Collectors.toMap(t -> t.getId().toString(), Function.identity()));
        List<StudyAi.QuestionDraft> usable = draft.questions().stream()
                .filter(q -> byKey.containsKey(q.topicKey())).toList();
        if (usable.isEmpty()) {
            throw new ApplicationException(ErrorCode.AI_UNAVAILABLE, "Could not generate questions, please retry");
        }
        return new Generated(interleave(usable, selected), byKey, sectionNodes);
    }

    // ------------------------------------------------------------------------------- tests a teacher sets

    /**
     * A question for a teacher to review before sending (classroom). Topics are identified by their position in the
     * learning path and sources by tree path, which every copy of the document shares.
     */
    public record DraftQuestion(int topicPosition, String topicTitle, QuestionType type, String prompt,
                                List<String> options, Integer correctOption, String referenceAnswer, String explanation,
                                List<String> sourcePaths) {
    }

    /** Drafts a full test over every topic of the user's document (nothing is saved). */
    public List<DraftQuestion> draftQuestions(UUID userId, UUID documentId, int count, AssessmentDifficulty difficulty,
                                              String format) {
        StudyMaterial material = access.requireByDocument(userId, documentId);
        List<StudyTopic> all = topics.findByMaterialIdOrderByPosition(material.getId());
        Generated generated = generate(userId, material, all, count, difficulty, format);
        Map<UUID, String> paths = new HashMap<>();
        store.nodes(documentId).forEach(n -> paths.put(n.id(), n.path()));
        return generated.questions().stream().map(q -> {
            StudyTopic topic = generated.byKey().get(q.topicKey());
            List<String> sources = q.sourceSectionIds() == null ? List.of() : q.sourceSectionIds().stream()
                    .map(generated.sectionNodes()::get).filter(Objects::nonNull).map(paths::get)
                    .filter(Objects::nonNull).distinct().toList();
            return new DraftQuestion(topic.getPosition(), topic.getTitle(), q.type(), q.prompt(),
                    q.options() == null ? List.of() : q.options(), q.correctOptionIndex(), q.referenceAnswer(),
                    q.explanation() == null ? "" : q.explanation(), sources);
        }).toList();
    }

    /** A question of an assigned test, as the teacher finalised it. */
    public record AssignedQuestion(int topicPosition, QuestionType type, String prompt, List<String> options,
                                   Integer correctOption, String referenceAnswer, String explanation,
                                   List<String> sourcePaths) {
    }

    /**
     * Creates a teacher's test on the student's copy of the document: an ordinary assessment (same runner, grading
     * and report) with the teacher's questions in the teacher's order, marked with who set it.
     *
     * @return the assessment's id
     */
    public UUID createAssigned(UUID studentId, UUID documentId, String title, String assignedBy, Instant dueAt,
                               AssessmentDifficulty difficulty, List<AssignedQuestion> assigned) {
        StudyMaterial material = access.requireByDocument(studentId, documentId);
        List<StudyTopic> path = topics.findByMaterialIdOrderByPosition(material.getId());
        if (path.isEmpty() || assigned.isEmpty()) {
            throw new ApplicationException(ErrorCode.INVALID_STATE, "This test has no questions to assign");
        }
        Map<Integer, StudyTopic> byPosition = path.stream()
                .collect(Collectors.toMap(StudyTopic::getPosition, Function.identity(), (a, b) -> a));
        Map<String, UUID> nodeByPath = new HashMap<>();
        store.nodes(documentId).forEach(n -> nodeByPath.put(n.path(), n.id()));
        return tx.execute(status -> {
            Instant now = clock.instant();
            Assessment assessment = new Assessment(studentId, material.getId(), difficulty, assigned.size(), now);
            assessment.assign(title, assignedBy, dueAt);
            assessments.save(assessment);
            int position = 1;
            for (AssignedQuestion q : assigned) {
                StudyTopic topic = byPosition.getOrDefault(q.topicPosition(), path.getFirst());
                StudyQuestion saved = questions.save(new StudyQuestion(material.getId(), topic.getId(), null,
                        QuestionOrigin.ASSESSMENT, null, position, q.type(), q.prompt(), q.options(), q.correctOption(),
                        q.referenceAnswer(), q.explanation(), q.sourcePaths().stream().map(nodeByPath::get)
                        .filter(Objects::nonNull).toList(), now));
                items.save(new AssessmentItem(assessment.getId(), position++, saved.getId()));
            }
            return assessment.getId();
        });
    }

    public List<AssessmentSummary> list(UUID userId, UUID documentId) {
        StudyMaterial material = access.requireByDocument(userId, documentId);
        return tx.execute(status -> assessments.findByUserIdAndMaterialIdOrderByCreatedAtDesc(userId, material.getId())
                .stream()
                .map(a -> new AssessmentSummary(a.getId(), a.getStatus(), a.getDifficulty(), a.getQuestionCount(),
                        items.countByAssessmentIdAndAttemptIdIsNotNull(a.getId()), a.getScore(), a.getCreatedAt(),
                        a.getCompletedAt(), AssessmentResponse.Assignment.of(a)))
                .toList());
    }

    public AssessmentResponse get(UUID userId, UUID assessmentId) {
        return tx.execute(status -> view(requireOwned(userId, assessmentId), userId));
    }

    /** Grades and records the answer, but reveals nothing about it: results come with the report. */
    public SubmittedAnswer answer(UUID userId, UUID assessmentId, int position, AnswerRequest request) {
        Assessment assessment = requireOwned(userId, assessmentId);
        AssessmentItem item = items.findByAssessmentIdAndPosition(assessmentId, position)
                .orElseThrow(() -> ApplicationException.notFound("Assessment item"));
        requireAnswerable(assessment, item);
        MaterialAccess.QuestionContext ctx = access.requireQuestion(userId, item.getQuestionId());
        AnswerGrader.Graded graded = grader.grade(ctx.question(), request, ctx.material().getLanguage(),
                access.sources(ctx.material(), ctx.question(), ctx.topic()));
        return tx.execute(status -> {
            Assessment locked = assessments.findForUpdate(assessmentId).orElseThrow();
            AssessmentItem fresh = items.findByAssessmentIdAndPosition(assessmentId, position).orElseThrow();
            requireAnswerable(locked, fresh); // re-check under lock: concurrent submissions
            AnswerRecorder.Recorded recorded = recorder.record(userId, ctx.question(), AttemptContext.ASSESSMENT,
                    assessmentId, graded, request.confidence());
            fresh.answer(recorded.attempt().getId());
            events.publishEvent(LearningActivityEvent.of(userId, LearningActivityEvent.Type.ASSESSMENT_ANSWERED,
                            clock.instant())
                    .document(ctx.material().getDocumentId(), ctx.material().getTitle())
                    .topic(ctx.topic().getId(), ctx.topic().getTitle())
                    .assessment(assessmentId)
                    .answer(ctx.question().getId(), graded.verdict(), request.confidence(), recorded.firstCorrect())
                    .build());
            return SubmittedAnswer.of(recorded.attempt());
        });
    }

    public AssessmentResponse complete(UUID userId, UUID assessmentId) {
        Assessment assessment = requireOwned(userId, assessmentId);
        if (!assessment.isOpen()) {
            return get(userId, assessmentId);
        }
        StudyMaterial material = access.requireMaterial(userId, assessment.getMaterialId());
        List<ReportBuilder.Answered> answered = tx.execute(status -> loadAnswered(assessmentId));
        AssessmentReport report = reports.build(material, answered); // may call the AI: outside the transaction
        tx.executeWithoutResult(status -> {
            Assessment locked = assessments.findForUpdate(assessmentId).orElseThrow();
            if (!locked.isOpen()) {
                return; // completed concurrently
            }
            Instant now = clock.instant();
            locked.complete(report.score(), json.writeValueAsString(report), now);
            answered.stream().filter(a -> a.attempt() == null)
                    .forEach(a -> recorder.scheduleUnanswered(userId, a.question()));
            events.publishEvent(LearningActivityEvent.of(userId, LearningActivityEvent.Type.ASSESSMENT_COMPLETED, now)
                    .document(material.getDocumentId(), material.getTitle())
                    .assessment(assessmentId).score(report.score()).build());
        });
        return get(userId, assessmentId);
    }

    /** (completed count, best score) of a learner on a material. */
    public record Stats(long completed, Double bestScore) {
    }

    public Stats stats(UUID userId, UUID materialId) {
        List<Assessment> list = assessments.findByUserIdAndMaterialIdOrderByCreatedAtDesc(userId, materialId);
        List<Double> scores = list.stream().filter(a -> !a.isOpen()).map(Assessment::getScore).toList();
        return new Stats(scores.size(), scores.stream().max(Double::compare).orElse(null));
    }

    // ---- internals

    private Assessment requireOwned(UUID userId, UUID assessmentId) {
        return assessments.findById(assessmentId).filter(a -> a.getUserId().equals(userId))
                .orElseThrow(() -> ApplicationException.notFound("Assessment"));
    }

    private static void requireAnswerable(Assessment assessment, AssessmentItem item) {
        if (!assessment.isOpen()) {
            throw new ApplicationException(ErrorCode.ASSESSMENT_CLOSED, "This assessment is already completed");
        }
        if (item.isAnswered()) {
            throw new ApplicationException(ErrorCode.ALREADY_ANSWERED, "This question was already answered");
        }
    }

    private List<ReportBuilder.Answered> loadAnswered(UUID assessmentId) {
        List<AssessmentItem> list = items.findByAssessmentIdOrderByPosition(assessmentId);
        Map<UUID, StudyQuestion> qs = questions.findAllById(list.stream().map(AssessmentItem::getQuestionId).toList())
                .stream().collect(Collectors.toMap(StudyQuestion::getId, Function.identity()));
        Map<UUID, StudyTopic> ts = topics.findAllById(qs.values().stream().map(StudyQuestion::getTopicId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(StudyTopic::getId, Function.identity()));
        Map<UUID, QuestionAttempt> as = attempts.findAllById(list.stream().map(AssessmentItem::getAttemptId)
                        .filter(Objects::nonNull).toList()).stream()
                .collect(Collectors.toMap(QuestionAttempt::getId, Function.identity()));
        return list.stream().map(i -> {
            StudyQuestion q = qs.get(i.getQuestionId());
            return new ReportBuilder.Answered(q, ts.get(q.getTopicId()),
                    i.getAttemptId() == null ? null : as.get(i.getAttemptId()));
        }).toList();
    }

    private AssessmentResponse view(Assessment a, UUID userId) {
        StudyMaterial material = access.requireMaterial(userId, a.getMaterialId());
        List<ReportBuilder.Answered> answered = loadAnswered(a.getId());
        List<AssessmentResponse.Item> views = new ArrayList<>();
        int position = 1;
        int answeredCount = 0;
        for (ReportBuilder.Answered x : answered) {
            SubmittedAnswer submitted = null;
            AnswerResult result = null;
            if (x.attempt() != null) {
                answeredCount++;
                submitted = SubmittedAnswer.of(x.attempt());
                if (!a.isOpen()) { // results only once the test is over
                    result = AnswerResult.of(x.question(), x.attempt(), cards.findByUserIdAndQuestionId(userId,
                            x.question().getId()).map(ReviewCard::getDueAt).orElse(null));
                }
            }
            views.add(new AssessmentResponse.Item(position++, QuestionView.of(x.question(), x.topic().getTitle()),
                    submitted, result));
        }
        AssessmentReport report = a.getReportJson() == null ? null
                : json.readValue(a.getReportJson(), AssessmentReport.class);
        return new AssessmentResponse(a.getId(), material.getDocumentId(), material.getTitle(), a.getStatus(),
                a.getDifficulty(), a.getQuestionCount(), answeredCount, a.getScore(), a.getCreatedAt(),
                a.getCompletedAt(), views, report, AssessmentResponse.Assignment.of(a));
    }

    /**
     * Interleaving: round-robin across topics (in path order), shuffled within each topic, so consecutive
     * questions rarely share a topic. See docs/architecture/learning-science.md.
     */
    static List<StudyAi.QuestionDraft> interleave(List<StudyAi.QuestionDraft> drafts, List<StudyTopic> topicOrder) {
        Map<String, List<StudyAi.QuestionDraft>> queues = new LinkedHashMap<>();
        topicOrder.forEach(t -> queues.put(t.getId().toString(), new ArrayList<>()));
        drafts.forEach(d -> queues.get(d.topicKey()).add(d));
        queues.values().forEach(Collections::shuffle);
        List<StudyAi.QuestionDraft> out = new ArrayList<>();
        Set<String> exhausted = new HashSet<>();
        while (exhausted.size() < queues.size()) {
            for (Map.Entry<String, List<StudyAi.QuestionDraft>> e : queues.entrySet()) {
                if (e.getValue().isEmpty()) {
                    exhausted.add(e.getKey());
                } else {
                    out.add(e.getValue().removeFirst());
                }
            }
        }
        return out;
    }
}
