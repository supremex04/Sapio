package com.ved.study.assess;

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

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.study.LearningActivityEvent;
import com.ved.study.ai.StudyAi;
import com.ved.study.material.MaterialAccess;
import com.ved.study.material.StudyMaterial;
import com.ved.study.material.StudyTopic;
import com.ved.study.material.StudyTopicRepository;
import com.ved.study.question.AnswerGrader;
import com.ved.study.question.AnswerRecorder;
import com.ved.study.question.AnswerRequest;
import com.ved.study.question.AnswerResult;
import com.ved.study.question.AttemptContext;
import com.ved.study.question.QuestionAttempt;
import com.ved.study.question.QuestionAttemptRepository;
import com.ved.study.question.QuestionOrigin;
import com.ved.study.question.QuestionView;
import com.ved.study.question.ReviewCard;
import com.ved.study.question.ReviewCardRepository;
import com.ved.study.question.StudyQuestion;
import com.ved.study.question.StudyQuestionRepository;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/** Test mode: generate an interleaved assessment, grade answers one by one, then build the report. */
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

    AssessmentService(MaterialAccess access, StudyAi ai, AssessmentRepository assessments,
                      AssessmentItemRepository items, StudyTopicRepository topics, StudyQuestionRepository questions,
                      QuestionAttemptRepository attempts, ReviewCardRepository cards, AnswerGrader grader,
                      AnswerRecorder recorder, ReportBuilder reports, ApplicationEventPublisher events,
                      TransactionTemplate tx, JsonMapper json, Clock clock) {
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
        List<String> avoid = questions.findAssessedPrompts(userId, material.getId());
        Map<String, UUID> sectionNodes = new HashMap<>(); // citation id ("§2.3") -> tree node
        List<StudyAi.QuestionTopic> topicSpecs = selected.stream().map(t -> {
            var sources = access.sources(material, t, MaterialAccess.QUESTION_BUDGET, 0);
            sources.sections().forEach(sec -> sectionNodes.put(sec.id(), sec.nodeId()));
            return new StudyAi.QuestionTopic(t.getId().toString(), t.getTitle(), t.getSummary(), t.getKeyConcepts(),
                    t.getDifficulty(), sources.sections());
        }).toList();
        StudyAi.QuestionsDraft draft = ai.generateQuestions(new StudyAi.QuestionSpec(material.getTitle(),
                material.getLanguage(), material.getKind(), request.questionCount(), request.difficulty().name(),
                topicSpecs, avoid.subList(Math.max(0, avoid.size() - MAX_AVOID_PROMPTS), avoid.size())));

        Map<String, StudyTopic> byKey = selected.stream()
                .collect(Collectors.toMap(t -> t.getId().toString(), Function.identity()));
        List<StudyAi.QuestionDraft> usable = draft.questions().stream()
                .filter(q -> byKey.containsKey(q.topicKey())).toList();
        if (usable.isEmpty()) {
            throw new ApplicationException(ErrorCode.AI_UNAVAILABLE, "Could not generate questions, please retry");
        }
        UUID id = tx.execute(status -> {
            Instant now = clock.instant();
            List<StudyAi.QuestionDraft> ordered = interleave(usable, selected);
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

    public List<AssessmentSummary> list(UUID userId, UUID documentId) {
        StudyMaterial material = access.requireByDocument(userId, documentId);
        return tx.execute(status -> assessments.findByUserIdAndMaterialIdOrderByCreatedAtDesc(userId, material.getId())
                .stream()
                .map(a -> new AssessmentSummary(a.getId(), a.getStatus(), a.getDifficulty(), a.getQuestionCount(),
                        items.countByAssessmentIdAndAttemptIdIsNotNull(a.getId()), a.getScore(), a.getCreatedAt(),
                        a.getCompletedAt()))
                .toList());
    }

    public AssessmentResponse get(UUID userId, UUID assessmentId) {
        return tx.execute(status -> view(requireOwned(userId, assessmentId), userId));
    }

    public AnswerResult answer(UUID userId, UUID assessmentId, int position, AnswerRequest request) {
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
            return AnswerResult.of(ctx.question(), recorded.attempt(), recorded.nextReviewAt());
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
            AnswerResult result = null;
            if (x.attempt() != null) {
                answeredCount++;
                result = AnswerResult.of(x.question(), x.attempt(), cards.findByUserIdAndQuestionId(userId,
                        x.question().getId()).map(ReviewCard::getDueAt).orElse(null));
            }
            views.add(new AssessmentResponse.Item(position++, QuestionView.of(x.question(), x.topic().getTitle()),
                    result));
        }
        AssessmentReport report = a.getReportJson() == null ? null
                : json.readValue(a.getReportJson(), AssessmentReport.class);
        return new AssessmentResponse(a.getId(), material.getDocumentId(), material.getTitle(), a.getStatus(),
                a.getDifficulty(), a.getQuestionCount(), answeredCount, a.getScore(), a.getCreatedAt(),
                a.getCompletedAt(), views, report);
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
