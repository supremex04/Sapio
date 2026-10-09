package com.ved.study.question;

import java.time.Instant;
import java.util.UUID;

/** Revealed only after answering: verdict plus the answer key and explanation. */
public record AnswerResult(
        UUID questionId,
        Verdict verdict,
        double score,
        Integer selectedOptionIndex,
        String answerText,
        Integer correctOptionIndex,
        String referenceAnswer,
        String explanation,
        String feedback,
        Confidence confidence,
        Instant nextReviewAt) {

    public static AnswerResult of(StudyQuestion q, QuestionAttempt a, Instant nextReviewAt) {
        return new AnswerResult(q.getId(), a.getVerdict(), a.getScore(), a.getSelectedOption(), a.getAnswerText(),
                q.getCorrectOption(), q.isChoice() ? null : q.getReferenceAnswer(), q.getExplanation(),
                a.getFeedback(), a.getConfidence(), nextReviewAt);
    }
}
