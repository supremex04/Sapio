package com.sapio.study;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Study tunings ({@code sapio.study.*} in application.yml). Context budgets are characters of retrieved source
 * text sent to the model per use: larger means better grounding but slower, costlier calls.
 */
@Validated
@ConfigurationProperties("sapio.study")
public record StudyProperties(@Valid @NotNull Context context) {

    public record Context(@Valid @NotNull Budget lesson, @Valid @NotNull Budget questions,
                          @Valid @NotNull Budget grade, @Valid @NotNull Budget tutor, @Valid @NotNull Budget ask,
                          @Valid @NotNull Budget notes) {
    }

    /** @param chars source text budget; @param figures max figures offered to the model */
    public record Budget(@Positive int chars, @PositiveOrZero int figures) {
    }
}
