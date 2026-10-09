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

    public record Item(int position, QuestionView question, AnswerResult answer) {
    }
}
