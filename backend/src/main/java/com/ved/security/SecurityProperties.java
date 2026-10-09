package com.ved.security;

import java.time.Duration;
import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties("ved.security")
public record SecurityProperties(@Valid @NotNull Jwt jwt, @Valid @NotNull RefreshToken refreshToken,
                                 @NotNull Cors cors) {

    public record Jwt(@NotBlank @Size(min = 32) String secret, @NotBlank String issuer,
                      @NotNull Duration accessTokenTtl) {
    }

    public record RefreshToken(@NotNull Duration ttl, @NotBlank String cookieName, @NotBlank String cookiePath,
                               boolean cookieSecure, @NotBlank String cookieSameSite) {
    }

    public record Cors(List<String> allowedOrigins) {
    }
}
