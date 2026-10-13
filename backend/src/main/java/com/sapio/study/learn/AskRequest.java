package com.sapio.study.learn;

import java.util.List;
import java.util.UUID;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/**
 * @param focus what the learner is looking at when they ask (optional), so "why?" is understood. Only ids are
 *              sent; the server resolves the content itself.
 */
public record AskRequest(@NotBlank @Size(max = 2000) String question, @Valid @Size(max = 20) List<Turn> history,
                         @Valid Focus focus) {

    public record Turn(@Pattern(regexp = "user|tutor") String role, @NotBlank @Size(max = 4000) String text) {
    }

    /** One of: a lesson section (by index) or a check question of this topic's lesson. */
    public record Focus(@PositiveOrZero Integer sectionIndex, UUID questionId) {
    }
}
