package com.ved.study.assess;

import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/** @param topicIds optional; empty or null = all topics */
public record CreateAssessmentRequest(@Min(3) @Max(20) int questionCount, @NotNull AssessmentDifficulty difficulty,
                                      List<UUID> topicIds) {
}
