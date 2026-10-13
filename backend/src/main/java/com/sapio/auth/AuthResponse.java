package com.sapio.auth;

import com.sapio.user.UserResponse;

/** Body returned on register/login/refresh. The refresh token travels separately as an HttpOnly cookie. */
public record AuthResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
