package com.sapio.visualization;

import java.net.URI;
import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** {@code sapio.visualization}: the renderer service and the limits on visualizations. */
@Validated
@ConfigurationProperties("sapio.visualization")
public record VisualizationProperties(@NotNull URI rendererUrl, @NotBlank String rendererApiKey,
                                      @NotNull Duration renderTimeout, @Min(1) int maxAttempts, @Min(0) int dailyLimit,
                                      @NotBlank String urlSecret, @NotNull Duration urlTtl) {
}
