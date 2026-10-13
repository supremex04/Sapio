package com.sapio.auth.session;

/** Request metadata recorded with a session (useful for "active devices" views and audits). */
public record ClientInfo(String userAgent, String ipAddress) {
}
