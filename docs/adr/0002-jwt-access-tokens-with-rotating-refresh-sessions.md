# 0002. Short-lived JWT + rotating refresh-token sessions

- Status: Accepted
- Date: 2026-10-09

## Context

We need login that survives reloads, supports per-device logout, scales horizontally, and
limits the damage of XSS or token theft. Options considered: server-side HTTP sessions
(Spring Session), long-lived JWTs, or short-lived JWTs + refresh tokens.

## Decision

- **Access token:** JWT (HS256), 15 minutes, held only in browser memory and sent as `Bearer`.
  Validated statelessly by Spring Security's resource server.
- **Refresh token:** opaque 256-bit random value in an `HttpOnly; SameSite=Strict; Path=/api/auth`
  cookie. Stored server-side as a SHA-256 hash in `user_sessions`, one row per login, **rotated on
  every refresh**.
- Passwords hashed with Spring's delegating encoder (bcrypt).

## Consequences

- API requests need no DB lookup for auth; sessions remain revocable (within 15 min) via the refresh row.
- Per-device sessions enable an "active devices" page later.
- Revoking access immediately (e.g. banning a user) waits for access-token expiry, at most 15 minutes.
  Acceptable for now; a deny-list or shorter TTL can be added.
- Moving to asymmetric signing (RS256 + JWKS) only means replacing the beans in `JwtConfig`.
- Refresh-token reuse detection (revoke the session when a rotated token is replayed) is not
  implemented yet; see roadmap.
