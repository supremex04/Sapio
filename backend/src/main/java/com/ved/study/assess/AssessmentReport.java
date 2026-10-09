package com.ved.study.assess;

import java.util.List;
import java.util.UUID;

/** Stored as JSON on the assessment when it completes. */
public record AssessmentReport(
        double score,
        int correctCount,
        int partialCount,
        int incorrectCount,
        int skippedCount,
        List<TopicReport> topics,
        String summary,
        List<String> strengths,
        List<WeakArea> weakAreas,
        List<String> nextSteps,
        Calibration calibration) {

    public enum Level {
        STRONG,
        DEVELOPING,
        WEAK;

        static Level of(double score) {
            return score >= 0.8 ? STRONG : score >= 0.5 ? DEVELOPING : WEAK;
        }
    }

    public record TopicReport(UUID topicId, String title, int questionCount, double score, Level level) {
    }

    public record WeakArea(UUID topicId, String title, String advice) {
    }

    /** Confident = MEDIUM or HIGH confidence; unsure = LOW. Answers without a rating are not counted. */
    public record Calibration(int confidentCorrect, int confidentIncorrect, int unsureCorrect, int unsureIncorrect) {
    }
}
