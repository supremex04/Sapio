package com.ved.study.assess;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import com.ved.study.question.AnswerResult;
import com.ved.study.question.QuestionView;

public record AssessmentResponse(
        UUID id,
        UUID documentId,
        String documentTitle,
        AssessmentStatus status,
        AssessmentDifficulty difficulty,
        int questionCount,
        int answeredCount,
        Double score,
        Instant createdAt,
        Instant completedAt,
        List<Item> items,
        AssessmentReport report) {

    /**
     * @param submitted what the learner answered (null if not yet)
     * @param answer    verdict, answer key and feedback: only once the test is completed (results are revealed
     *                  in the report, never during the test)
     */
    public record Item(int position, QuestionView question, SubmittedAnswer submitted, AnswerResult answer) {
    }
}
