package com.ved.document.ocr;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ved.inference")
public record InferenceProperties(@NotNull URI baseUrl, @NotBlank String apiKey, @NotNull Duration connectTimeout,
                                  @NotNull Duration readTimeout) {
}
