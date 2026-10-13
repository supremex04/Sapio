package com.sapio.auth.session;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.security.SecurityProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Owns the refresh-token session lifecycle: open, rotate, revoke.
 */
@Service
public class SessionService {

    private final UserSessionRepository sessions;
    private final Clock clock;
    private final SecurityProperties.RefreshToken config;

    SessionService(UserSessionRepository sessions, Clock clock, SecurityProperties properties) {
        this.sessions = sessions;
        this.clock = clock;
        this.config = properties.refreshToken();
    }

    /** The raw refresh token handed to the client plus the session it belongs to. */
    public record IssuedSession(UUID sessionId, UUID userId, String refreshToken, Instant expiresAt) {
    }

    @Transactional
    public IssuedSession open(UUID userId, ClientInfo client) {
        Instant now = clock.instant();
        String token = RefreshTokens.generate();
        UserSession session = sessions.save(
                new UserSession(userId, RefreshTokens.hash(token), client, now, now.plus(config.ttl())));
        return new IssuedSession(session.getId(), userId, token, session.getExpiresAt());
    }

    /**
     * Exchanges a valid refresh token for a new one (rotation). The old token stops working immediately.
     */
    @Transactional
    public IssuedSession rotate(String rawRefreshToken) {
        Instant now = clock.instant();
        UserSession session = findActive(rawRefreshToken, now);
        String token = RefreshTokens.generate();
        session.rotate(RefreshTokens.hash(token), now, now.plus(config.ttl()));
        return new IssuedSession(session.getId(), session.getUserId(), token, session.getExpiresAt());
    }

    /** Revokes the session for this token if it exists. Idempotent: unknown tokens are ignored. */
    @Transactional
    public void revoke(String rawRefreshToken) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            return;
        }
        sessions.findByRefreshTokenHash(RefreshTokens.hash(rawRefreshToken))
                .ifPresent(session -> session.revoke(clock.instant()));
    }

    private UserSession findActive(String rawRefreshToken, Instant now) {
        if (rawRefreshToken == null || rawRefreshToken.isBlank()) {
            throw invalidToken();
        }
        return sessions.findByRefreshTokenHash(RefreshTokens.hash(rawRefreshToken))
                .filter(session -> session.isActive(now))
                .orElseThrow(SessionService::invalidToken);
    }

    private static ApplicationException invalidToken() {
        return new ApplicationException(ErrorCode.INVALID_REFRESH_TOKEN, "Session expired or invalid");
    }
}
