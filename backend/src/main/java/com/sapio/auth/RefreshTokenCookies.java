package com.sapio.auth;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.Optional;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

import com.sapio.security.SecurityProperties;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * Reads and writes the refresh-token cookie. HttpOnly keeps it away from JavaScript; the narrow path
 * means browsers only send it to the auth endpoints.
 */
@Component
class RefreshTokenCookies {

    private final SecurityProperties.RefreshToken config;
    private final Clock clock;

    RefreshTokenCookies(SecurityProperties properties, Clock clock) {
        this.config = properties.refreshToken();
        this.clock = clock;
    }

    ResponseCookie create(String refreshToken, Instant expiresAt) {
        Duration maxAge = Duration.between(clock.instant(), expiresAt);
        return base(refreshToken).maxAge(maxAge).build();
    }

    ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    Optional<String> read(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return Optional.empty();
        }
        return Arrays.stream(cookies)
                .filter(c -> config.cookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .filter(v -> !v.isBlank())
                .findFirst();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(config.cookieName(), value)
                .httpOnly(true)
                .secure(config.cookieSecure())
                .sameSite(config.cookieSameSite())
                .path(config.cookiePath());
    }
}
