package com.sapio.document;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sapio.documents")
/**
 * @param maxSizeBytes  documents (PDF, Word, images)
 * @param maxMediaBytes audio and video (lecture recordings, course videos)
 * @param mediaUrlSecret signs playback URLs so audio / video elements can stream without an Authorization header
 */
public record DocumentProperties(@NotNull Path storageDir, @Positive long maxSizeBytes, @Positive long maxMediaBytes,
                                 @NotBlank String mediaUrlSecret, @NotNull Duration mediaUrlTtl,
                                 @Valid @NotNull Processing processing) {

    /**
     * @param autoRetryDelays waits before each automatic retry of a transiently failed document
     * @param lease           an in-flight document whose heartbeat is older than this is considered abandoned and
     *                        requeued (by any instance); keep it several times
     *                        {@code sapio.documents.processing.heartbeat-interval}
     */
    public record Processing(@Positive int corePoolSize, @Positive int maxPoolSize, @Positive int queueCapacity,
                             @NotNull List<Duration> autoRetryDelays, @NotNull Duration lease) {
    }
}
