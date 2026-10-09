-- Identity: users and their login sessions (one row per refresh-token holder / device).

CREATE TABLE users
(
    id            UUID PRIMARY KEY,
    email         VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(100) NOT NULL,
    full_name     VARCHAR(120) NOT NULL,
    role          VARCHAR(20)  NOT NULL,
    enabled       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at    TIMESTAMPTZ  NOT NULL,
    updated_at    TIMESTAMPTZ  NOT NULL
);

CREATE TABLE user_sessions
(
    id                 UUID PRIMARY KEY,
    user_id            UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- SHA-256 hex of the opaque refresh token; the raw token is never stored.
    refresh_token_hash VARCHAR(64) NOT NULL UNIQUE,
    user_agent         VARCHAR(512),
    ip_address         VARCHAR(64),
    created_at         TIMESTAMPTZ NOT NULL,
    last_used_at       TIMESTAMPTZ NOT NULL,
    expires_at         TIMESTAMPTZ NOT NULL,
    revoked_at         TIMESTAMPTZ
);

CREATE INDEX ix_user_sessions_user_id ON user_sessions (user_id);
