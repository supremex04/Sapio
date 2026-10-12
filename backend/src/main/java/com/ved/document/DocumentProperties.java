package com.ved.document;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ved.documents")
public record DocumentProperties(@NotNull Path storageDir, @Positive long maxSizeBytes,
                                 @Valid @NotNull Processing processing) {

    /**
     * @param autoRetryDelays waits before each automatic retry of a transiently failed document
     * @param lease           an in-flight document whose heartbeat is older than this is considered abandoned and
     *                        requeued (by any instance); heartbeats are sent at a quarter of it
     */
    public record Processing(@Positive int corePoolSize, @Positive int maxPoolSize, @Positive int queueCapacity,
                             @NotNull List<Duration> autoRetryDelays, @NotNull Duration lease) {
    }
}
