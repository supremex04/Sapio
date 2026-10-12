package com.ved.sharing;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ved.sharing")
public record SharingProperties(@Valid @NotNull Comments comments) {

    /**
     * @param maxLength characters per comment (at most the column size)
     * @param perMinute comments one learner may post per minute, across all shares
     */
    public record Comments(@Positive @Max(2000) int maxLength, @Positive int perMinute) {
    }
}
