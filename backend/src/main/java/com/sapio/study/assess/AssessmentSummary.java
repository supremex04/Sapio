package com.sapio.study.assess;

import java.time.Instant;
import java.util.UUID;

public record AssessmentSummary(UUID id, AssessmentStatus status, AssessmentDifficulty difficulty, int questionCount,
                                long answeredCount, Double score, Instant createdAt, Instant completedAt,
                                AssessmentResponse.Assignment assignment) {
}
