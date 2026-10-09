package com.ved.auth;

import java.time.Instant;
import java.util.UUID;

/** A user signed in with credentials (or registered). Consumed by analytics (logins per day). */
public record UserLoggedInEvent(UUID userId, Instant occurredAt) {
}
