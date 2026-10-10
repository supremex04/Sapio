package com.ved.study.learn;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ved.knowledge.figure.FigureViews;
import com.ved.knowledge.retrieval.ContextAssembler;
import com.ved.study.LearningActivityEvent;
import com.ved.study.StudyProperties;
import com.ved.study.ai.StudyAi;
import com.ved.study.material.MaterialAccess;
import com.ved.study.material.MaterialAccess.TopicContext;
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
import com.ved.study.question.ReviewCardRepository;
import com.ved.study.question.StudyQuestion;
import com.ved.study.question.StudyQuestionRepository;
import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/**
 * Learn mode: lessons (generated once per topic, then cached), comprehension checks, topic completion
 * and the tutor. AI calls run outside transactions.
 */
@Service
public class LessonService {

    private static final TypeReference<List<LessonSection>> SECTIONS = new TypeReference<>() {
    };

    private final MaterialAccess access;
    private final StudyAi ai;
    private final StudyLessonRepository lessons;
    private final StudyTopicRepository topics;
    private final StudyQuestionRepository questions;
    private final QuestionAttemptRepository attempts;
    private final ReviewCardRepository cards;
    private final TopicProgressRepository progress;
    private final AnswerGrader grader;
    private final AnswerRecorder recorder;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final JsonMapper json;
    private final Clock clock;
    private final ContextAssembler context;
    private final FigureViews figures;

    LessonService(MaterialAccess access, StudyAi ai, StudyLessonRepository lessons, StudyTopicRepository topics,
                  StudyQuestionRepository questions, QuestionAttemptRepository attempts, ReviewCardRepository cards,
                  TopicProgressRepository progress, AnswerGrader grader, AnswerRecorder recorder,
                  ApplicationEventPublisher events, TransactionTemplate tx, JsonMapper json, Clock clock,
                  ContextAssembler context, FigureViews figures) {
        this.context = context;
        this.figures = figures;
        this.access = access;
        this.ai = ai;
        this.lessons = lessons;
        this.topics = topics;
        this.questions = questions;
        this.attempts = attempts;
        this.cards = cards;
        this.progress = progress;
        this.grader = grader;
        this.recorder = recorder;
        this.events = events;
        this.tx = tx;
        this.json = json;
        this.clock = clock;
    }

    public LessonResponse getLesson(UUID userId, UUID topicId) {
        TopicContext ctx = access.requireTopic(userId, topicId);
        StudyLesson lesson = lessons.findByTopicId(topicId).orElseGet(() -> generate(ctx));
        return tx.execute(status -> {
            TopicStatus topicStatus = start(userId, ctx);
            List<StudyQuestion> checks = questions.findByLessonIdOrderByPosition(lesson.getId());
            Map<UUID, QuestionAttempt> latest = latestAttempts(userId, checks);
            List<LessonResponse.Check> views = checks.stream().map(q -> new LessonResponse.Check(q.getId(),
                    q.getAfterSection(), q.getType(), q.getPrompt(), q.getOptions(),
                    latest.containsKey(q.getId()) ? AnswerResult.of(q, latest.get(q.getId()), nextReview(userId, q))
                            : null)).toList();
            return new LessonResponse(topicId, ctx.topic().getTitle(), json.readValue(lesson.getSectionsJson(),
                    SECTIONS), lesson.getKeyTakeaways(), views, topicStatus, figures.byIds(lesson.getFigureIds()));
        });
    }

    public AnswerResult answerCheck(UUID userId, UUID questionId, AnswerRequest request) {
        MaterialAccess.QuestionContext ctx = access.requireQuestion(userId, questionId);
        StudyQuestion question = ctx.question();
        if (question.getOrigin() != QuestionOrigin.LESSON_CHECK) {
            throw ApplicationException.notFound("Question");
        }
        AnswerGrader.Graded graded = grader.grade(question, request, ctx.material().getLanguage(),
                access.sources(ctx.material(), question, ctx.topic()));
        return tx.execute(status -> {
            AnswerRecorder.Recorded recorded = recorder.record(userId, question, AttemptContext.LESSON, null, graded,
                    request.confidence());
            publish(userId, LearningActivityEvent.Type.CHECK_ANSWERED, ctx.material(), ctx.topic(), b -> b.answer(
                    question.getId(), graded.verdict(), request.confidence(), recorded.firstCorrect()));
            return AnswerResult.of(question, recorded.attempt(), recorded.nextReviewAt());
        });
    }

    public TopicProgressResponse complete(UUID userId, UUID topicId) {
        TopicContext ctx = access.requireTopic(userId, topicId);
        return tx.execute(status -> {
            Instant now = clock.instant();
            TopicProgress p = progress.findByUserIdAndTopicId(userId, topicId)
                    .orElseGet(() -> progress.save(new TopicProgress(userId, topicId, now)));
            if (p.complete(now)) {
                publish(userId, LearningActivityEvent.Type.TOPIC_COMPLETED, ctx.material(), ctx.topic(), b -> b);
            }
            return new TopicProgressResponse(topicId, p.getStatus(), p.getCompletedAt());
        });
    }

    /** The tutor: hybrid retrieval scoped to the topic's sections, short conversational answer. */
    public AskResponse ask(UUID userId, UUID topicId, AskRequest request) {
        TopicContext ctx = access.requireTopic(userId, topicId);
        List<StudyAi.ChatTurn> history = request.history() == null ? List.of() : request.history().stream()
                .map(t -> new StudyAi.ChatTurn(t.role(), t.text())).toList();
        StudyProperties.Budget budget = access.budgets().tutor();
        ContextAssembler.Context sources = context.forQuestion(ctx.material().getDocumentId(), request.question(),
                ctx.topic().getNodeIds(), budget.chars(), budget.figures());
        if (sources.isEmpty()) {
            sources = access.sources(ctx.material(), ctx.topic(), budget);
        }
        StudyAi.AnswerDraft reply = ai.answer(new StudyAi.AnswerSpec(ctx.material().getTitle(),
                ctx.material().getLanguage(), request.question(), history, sources.sections(), sources.figures(),
                "tutor"));
        tx.executeWithoutResult(status -> publish(userId, LearningActivityEvent.Type.TUTOR_QUESTION, ctx.material(),
                ctx.topic(), b -> b));
        return new AskResponse(reply.answer(), reply.grounded(), figures.byIds(uuids(reply.figureIds())));
    }

    // ---- internals

    private StudyLesson generate(TopicContext ctx) {
        List<StudyTopic> path = topics.findByMaterialIdOrderByPosition(ctx.material().getId());
        int index = path.indexOf(path.stream().filter(t -> t.getId().equals(ctx.topic().getId())).findFirst()
                .orElseThrow());
        StudyTopic topic = ctx.topic();
        ContextAssembler.Context sources = access.sources(ctx.material(), topic, access.budgets().lesson());
        StudyAi.LessonDraft draft = ai.generateLesson(new StudyAi.LessonSpec(ctx.material().getTitle(),
                ctx.material().getLanguage(), ctx.material().getKind(),
                new StudyAi.TopicBrief(topic.getTitle(), topic.getSummary(), topic.getKeyConcepts(),
                        topic.getDifficulty()),
                path.subList(0, index).stream().map(StudyTopic::getTitle).toList(),
                path.subList(index + 1, path.size()).stream().map(StudyTopic::getTitle).toList(),
                sources.sections(), sources.figures()));
        if (draft.sections() == null || draft.sections().isEmpty()) {
            throw new ApplicationException(ErrorCode.AI_UNAVAILABLE, "The lesson came back empty, please retry");
        }
        try {
            return tx.execute(status -> persist(ctx, draft));
        } catch (DataIntegrityViolationException e) {
            // Another request generated this lesson concurrently; use theirs.
            return lessons.findByTopicId(topic.getId()).orElseThrow(() -> e);
        }
    }

    private StudyLesson persist(TopicContext ctx, StudyAi.LessonDraft draft) {
        Instant now = clock.instant();
        List<LessonSection> sections = new ArrayList<>();
        for (int i = 0; i < draft.sections().size(); i++) {
            StudyAi.SectionDraft s = draft.sections().get(i);
            sections.add(new LessonSection(i, s.kind(), s.heading(), s.body()));
        }
        StudyLesson lesson = lessons.saveAndFlush(new StudyLesson(ctx.topic().getId(), json.writeValueAsString(sections),
                draft.keyTakeaways() == null ? List.of() : draft.keyTakeaways(), uuids(draft.figureIds()), draft.model(),
                now));
        int position = 0;
        for (StudyAi.CheckDraft c : draft.checks()) {
            questions.save(new StudyQuestion(ctx.material().getId(), ctx.topic().getId(), lesson.getId(),
                    QuestionOrigin.LESSON_CHECK, c.afterSection(), position++, c.type(), c.prompt(),
                    c.options() == null ? List.of() : c.options(), c.correctOptionIndex(), c.referenceAnswer(),
                    c.explanation() == null ? "" : c.explanation(), ctx.topic().getNodeIds(), now));
        }
        return lesson;
    }

    /** Marks the topic in progress on first open (and publishes LESSON_STARTED once). */
    private TopicStatus start(UUID userId, TopicContext ctx) {
        Instant now = clock.instant();
        return progress.findByUserIdAndTopicId(userId, ctx.topic().getId())
                .map(p -> {
                    p.touch(now);
                    return p.getStatus();
                })
                .orElseGet(() -> {
                    progress.save(new TopicProgress(userId, ctx.topic().getId(), now));
                    publish(userId, LearningActivityEvent.Type.LESSON_STARTED, ctx.material(), ctx.topic(), b -> b);
                    return TopicStatus.IN_PROGRESS;
                });
    }

    static List<UUID> uuids(List<String> ids) {
        if (ids == null) {
            return List.of();
        }
        List<UUID> out = new ArrayList<>();
        for (String id : ids) {
            try {
                out.add(UUID.fromString(id));
            } catch (IllegalArgumentException ignored) {
                // not a figure id we issued; the inference service already filters these
            }
        }
        return out;
    }

    private Map<UUID, QuestionAttempt> latestAttempts(UUID userId, List<StudyQuestion> checks) {
        return attempts.findByUserIdAndQuestionIdInOrderByCreatedAtDesc(userId,
                        checks.stream().map(StudyQuestion::getId).toList()).stream()
                .collect(Collectors.toMap(QuestionAttempt::getQuestionId, Function.identity(), (a, b) -> a));
    }

    private Instant nextReview(UUID userId, StudyQuestion q) {
        return cards.findByUserIdAndQuestionId(userId, q.getId()).map(c -> c.getDueAt()).orElse(null);
    }

    private void publish(UUID userId, LearningActivityEvent.Type type, StudyMaterial material, StudyTopic topic,
                         Function<LearningActivityEvent.Builder, LearningActivityEvent.Builder> extra) {
        events.publishEvent(extra.apply(LearningActivityEvent.of(userId, type, clock.instant())
                .document(material.getDocumentId(), material.getTitle())
                .topic(topic.getId(), topic.getTitle())).build());
    }
}
