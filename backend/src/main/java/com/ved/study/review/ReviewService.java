package com.ved.study.review;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import com.ved.study.LearningActivityEvent;
import com.ved.study.material.MaterialAccess;
import com.ved.study.question.AnswerGrader;
import com.ved.study.question.AnswerRecorder;
import com.ved.study.question.AnswerRequest;
import com.ved.study.question.AnswerResult;
import com.ved.study.question.AttemptContext;
import com.ved.study.question.QuestionView;
import com.ved.study.question.ReviewCard;
import com.ved.study.question.ReviewCardRepository;
import com.ved.user.UserService;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

/** Spaced review sessions: what's due, and answering due cards (which moves them through the Leitner boxes). */
@Service
public class ReviewService {

    static final int MAX_SESSION = 20;

    private final ReviewCardRepository cards;
    private final MaterialAccess access;
    private final AnswerGrader grader;
    private final AnswerRecorder recorder;
    private final UserService users;
    private final ApplicationEventPublisher events;
    private final TransactionTemplate tx;
    private final Clock clock;

    ReviewService(ReviewCardRepository cards, MaterialAccess access, AnswerGrader grader, AnswerRecorder recorder,
                  UserService users, ApplicationEventPublisher events, TransactionTemplate tx, Clock clock) {
        this.cards = cards;
        this.access = access;
        this.grader = grader;
        this.recorder = recorder;
        this.users = users;
        this.events = events;
        this.tx = tx;
        this.clock = clock;
    }

    public ReviewSummary summary(UUID userId) {
        Instant now = clock.instant();
        var zone = users.getById(userId).zoneId();
        Instant endOfDay = LocalDate.now(clock.withZone(zone)).plusDays(1).atStartOfDay(zone).toInstant();
        return new ReviewSummary(cards.countByUserIdAndDueAtLessThanEqual(userId, now),
                cards.countByUserIdAndDueAtLessThanEqual(userId, endOfDay), cards.countByUserId(userId));
    }

    /** Due cards, interleaved across topics (round-robin) for better discrimination between ideas. */
    public List<ReviewItem> due(UUID userId, int limit, UUID documentId) {
        int size = Math.clamp(limit, 1, MAX_SESSION);
        Instant now = clock.instant();
        return tx.execute(status -> {
            List<ReviewCard> candidates = documentId == null
                    ? cards.findByUserIdAndDueAtLessThanEqualOrderByDueAt(userId, now, Limit.of(size * 3))
                    : cards.findDueForDocument(userId, now, documentId, Limit.of(size * 3));
            Map<UUID, List<ReviewItem>> byTopic = new LinkedHashMap<>();
            for (ReviewCard card : candidates) {
                MaterialAccess.QuestionContext ctx = access.requireQuestion(userId, card.getQuestionId());
                byTopic.computeIfAbsent(ctx.topic().getId(), k -> new ArrayList<>()).add(new ReviewItem(
                        QuestionView.of(ctx.question(), ctx.topic().getTitle()), ctx.material().getDocumentId(),
                        ctx.material().getTitle(), card.getBox(), card.getDueAt()));
            }
            List<ReviewItem> out = new ArrayList<>();
            while (out.size() < size && byTopic.values().stream().anyMatch(l -> !l.isEmpty())) {
                for (List<ReviewItem> queue : byTopic.values()) {
                    if (!queue.isEmpty() && out.size() < size) {
                        out.add(queue.removeFirst());
                    }
                }
            }
            return out;
        });
    }

    public AnswerResult answer(UUID userId, UUID questionId, AnswerRequest request) {
        MaterialAccess.QuestionContext ctx = access.requireQuestion(userId, questionId);
        ReviewCard card = cards.findByUserIdAndQuestionId(userId, questionId)
                .orElseThrow(() -> ApplicationException.notFound("Review card"));
        if (card.getDueAt().isAfter(clock.instant())) {
            throw new ApplicationException(ErrorCode.NOT_DUE, "This card is not due yet");
        }
        AnswerGrader.Graded graded = grader.grade(ctx.question(), request, ctx.material().getLanguage(),
                access.sources(ctx.material(), ctx.question(), ctx.topic()));
        return tx.execute(status -> {
            AnswerRecorder.Recorded recorded = recorder.record(userId, ctx.question(), AttemptContext.REVIEW, null,
                    graded, request.confidence());
            events.publishEvent(LearningActivityEvent.of(userId, LearningActivityEvent.Type.REVIEW_ANSWERED,
                            clock.instant())
                    .document(ctx.material().getDocumentId(), ctx.material().getTitle())
                    .topic(ctx.topic().getId(), ctx.topic().getTitle())
                    .answer(questionId, graded.verdict(), request.confidence(), recorded.firstCorrect())
                    .build());
            return AnswerResult.of(ctx.question(), recorded.attempt(), recorded.nextReviewAt());
        });
    }
}
