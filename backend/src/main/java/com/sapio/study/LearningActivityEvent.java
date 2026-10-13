package com.sapio.study;

import java.time.Instant;
import java.util.UUID;

import com.sapio.study.question.Confidence;
import com.sapio.study.question.Verdict;

/**
 * A learning action happened. Published inside the action's transaction; consumed by the progress
 * module (XP, streaks, analytics). Titles are snapshots for the activity feed.
 */
public record LearningActivityEvent(
        UUID userId,
        Type type,
        UUID documentId,
        String documentTitle,
        UUID topicId,
        String topicTitle,
        UUID assessmentId,
        UUID questionId,
        Verdict verdict,
        Confidence confidence,
        boolean firstCorrect,
        Double score,
        Instant occurredAt) {

    public enum Type {
        LESSON_STARTED,
        CHECK_ANSWERED,
        TOPIC_COMPLETED,
        TUTOR_QUESTION,
        ASSESSMENT_STARTED,
        ASSESSMENT_ANSWERED,
        ASSESSMENT_COMPLETED,
        REVIEW_ANSWERED
    }

    public static Builder of(UUID userId, Type type, Instant at) {
        return new Builder(userId, type, at);
    }

    /** Keeps call sites readable: only the relevant fields are set. */
    public static final class Builder {
        private final UUID userId;
        private final Type type;
        private final Instant at;
        private UUID documentId;
        private String documentTitle;
        private UUID topicId;
        private String topicTitle;
        private UUID assessmentId;
        private UUID questionId;
        private Verdict verdict;
        private Confidence confidence;
        private boolean firstCorrect;
        private Double score;

        private Builder(UUID userId, Type type, Instant at) {
            this.userId = userId;
            this.type = type;
            this.at = at;
        }

        public Builder document(UUID id, String title) {
            this.documentId = id;
            this.documentTitle = title;
            return this;
        }

        public Builder topic(UUID id, String title) {
            this.topicId = id;
            this.topicTitle = title;
            return this;
        }

        public Builder assessment(UUID id) {
            this.assessmentId = id;
            return this;
        }

        public Builder answer(UUID questionId, Verdict verdict, Confidence confidence, boolean firstCorrect) {
            this.questionId = questionId;
            this.verdict = verdict;
            this.confidence = confidence;
            this.firstCorrect = firstCorrect;
            return this;
        }

        public Builder score(double score) {
            this.score = score;
            return this;
        }

        public LearningActivityEvent build() {
            return new LearningActivityEvent(userId, type, documentId, documentTitle, topicId, topicTitle,
                    assessmentId, questionId, verdict, confidence, firstCorrect, score, at);
        }
    }
}
