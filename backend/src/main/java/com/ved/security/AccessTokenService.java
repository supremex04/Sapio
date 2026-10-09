package com.ved.security;

import com.ved.user.User;

/**
 * Issues short-lived access tokens for authenticated users. Kept as an abstraction so the token
 * format (JWT today) can change without touching the auth flow.
 */
public interface AccessTokenService {

    AccessToken issue(User user);

    record AccessToken(String value, long expiresInSeconds) {
    }
}
