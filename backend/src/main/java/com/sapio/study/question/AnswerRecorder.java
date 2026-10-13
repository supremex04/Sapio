package com.sapio.study.question;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists a graded answer and keeps the learner's spaced-repetition card in step. Every mode
 * (lesson, assessment, review) records answers through here.
 */
@Component
public class AnswerRecorder {

    private final QuestionAttemptRepository attempts;
    private final ReviewCardRepository cards;
    private final ReviewSchedule schedule;
    private final Clock clock;

    AnswerRecorder(QuestionAttemptRepository attempts, ReviewCardRepository cards, ReviewSchedule schedule,
                   Clock clock) {
        this.attempts = attempts;
        this.cards = cards;
        this.schedule = schedule;
        this.clock = clock;
    }

    /**
     * @param firstCorrect true when this is the learner's first correct answer to the question (XP rule)
     */
    public record Recorded(QuestionAttempt attempt, Instant nextReviewAt, boolean firstCorrect) {
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Recorded record(UUID userId, StudyQuestion question, AttemptContext context, UUID assessmentId,
                           AnswerGrader.Graded graded, Confidence confidence) {
        Instant now = clock.instant();
        boolean firstCorrect = graded.verdict() == Verdict.CORRECT
                && !attempts.existsByUserIdAndQuestionIdAndVerdict(userId, question.getId(), Verdict.CORRECT);
        QuestionAttempt attempt = attempts.save(new QuestionAttempt(userId, question.getId(), context, assessmentId,
                graded.selectedOption(), graded.answerText(), graded.verdict(), graded.score(), confidence,
                graded.feedback(), now));

        ReviewCard card = cards.findByUserIdAndQuestionId(userId, question.getId()).orElse(null);
        if (card == null) {
            card = cards.save(new ReviewCard(userId, question.getId(), schedule.first(graded.verdict(), now), now));
        } else if (context == AttemptContext.REVIEW) {
            card.reschedule(schedule.next(card.getBox(), graded.verdict(), now),
                    graded.verdict() == Verdict.INCORRECT, now);
        }
        return new Recorded(attempt, card.getDueAt(), firstCorrect);
    }

    /**
     * A question the learner saw but skipped (e.g. finishing a test early) is scheduled like a miss, so it
     * comes back in spaced review instead of silently disappearing.
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void scheduleUnanswered(UUID userId, StudyQuestion question) {
        if (cards.findByUserIdAndQuestionId(userId, question.getId()).isEmpty()) {
            Instant now = clock.instant();
            cards.save(new ReviewCard(userId, question.getId(), schedule.first(Verdict.INCORRECT, now), now));
        }
    }
}
