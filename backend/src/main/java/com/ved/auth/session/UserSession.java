package com.ved.auth.session;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * A login session for one device/browser. It is identified by the hash of its current refresh token;
 * the token is rotated on every refresh, so a stolen-but-already-used token is useless.
 */
@Entity
@Table(name = "user_sessions")
public class UserSession {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "refresh_token_hash", nullable = false, unique = true, length = 64)
    private String refreshTokenHash;

    @Column(name = "user_agent", length = 512)
    private String userAgent;

    @Column(name = "ip_address", length = 64)
    private String ipAddress;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "last_used_at", nullable = false)
    private Instant lastUsedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    protected UserSession() {
        // for JPA
    }

    UserSession(UUID userId, String refreshTokenHash, ClientInfo client, Instant now, Instant expiresAt) {
        this.userId = userId;
        this.refreshTokenHash = refreshTokenHash;
        this.userAgent = truncate(client.userAgent(), 512);
        this.ipAddress = truncate(client.ipAddress(), 64);
        this.createdAt = now;
        this.lastUsedAt = now;
        this.expiresAt = expiresAt;
    }

    boolean isActive(Instant now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    void rotate(String newRefreshTokenHash, Instant now, Instant newExpiresAt) {
        this.refreshTokenHash = newRefreshTokenHash;
        this.lastUsedAt = now;
        this.expiresAt = newExpiresAt;
    }

    void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    private static String truncate(String value, int max) {
        return value == null || value.length() <= max ? value : value.substring(0, max);
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }
}
