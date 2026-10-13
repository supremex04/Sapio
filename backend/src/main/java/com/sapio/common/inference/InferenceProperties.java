package com.sapio.common.inference;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("sapio.inference")
/**
 * @param mediaReadTimeout transcription of long recordings (hours of audio on a CPU take a while)
 */
public record InferenceProperties(@NotNull URI baseUrl, @NotBlank String apiKey, @NotNull Duration connectTimeout,
                                  @NotNull Duration readTimeout, @NotNull Duration mediaReadTimeout) {
}
