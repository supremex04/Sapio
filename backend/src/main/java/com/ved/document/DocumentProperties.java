package com.ved.document;

import java.nio.file.Path;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ved.documents")
public record DocumentProperties(@NotNull Path storageDir, @Positive long maxSizeBytes,
                                 @Valid @NotNull Processing processing) {

    public record Processing(@Positive int corePoolSize, @Positive int maxPoolSize, @Positive int queueCapacity) {
    }
}
