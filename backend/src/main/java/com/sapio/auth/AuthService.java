package com.sapio.auth;

import java.time.Clock;
import java.time.Instant;

import com.sapio.auth.session.ClientInfo;
import com.sapio.auth.session.SessionService;
import com.sapio.auth.session.SessionService.IssuedSession;
import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.security.AccessTokenService;
import com.sapio.security.AccessTokenService.AccessToken;
import com.sapio.user.User;
import com.sapio.user.UserRepository;
import com.sapio.user.UserResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Orchestrates the authentication use-cases. Token formats and session storage are delegated to
 * {@link AccessTokenService} and {@link SessionService}.
 */
@Service
public class AuthService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AccessTokenService accessTokens;
    private final SessionService sessions;
    private final ApplicationEventPublisher events;
    private final Clock clock;
    /** Compared against when the email is unknown, so response timing doesn't reveal registered emails. */
    private final String dummyPasswordHash;

    AuthService(UserRepository users, PasswordEncoder passwordEncoder, AccessTokenService accessTokens,
                SessionService sessions, ApplicationEventPublisher events, Clock clock) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.accessTokens = accessTokens;
        this.sessions = sessions;
        this.events = events;
        this.clock = clock;
        this.dummyPasswordHash = passwordEncoder.encode("timing-equalizer-password");
    }

    /** Result of a successful authentication: API response body plus the refresh token for the cookie. */
    public record AuthResult(AuthResponse response, String refreshToken, Instant refreshTokenExpiresAt) {
    }

    @Transactional
    public AuthResult register(RegisterRequest request, ClientInfo client) {
        if (!request.role().isSelfAssignable()) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED, "Role cannot be self-assigned");
        }
        String email = User.normalizeEmail(request.email());
        if (users.existsByEmail(email)) {
            throw new ApplicationException(ErrorCode.EMAIL_ALREADY_REGISTERED, "Email is already registered");
        }
        User user = new User(email, passwordEncoder.encode(request.password()), request.fullName(),request.age(), request.role());
        user.updateTimeZone(request.timeZone());
        user = users.save(user);
        events.publishEvent(new UserLoggedInEvent(user.getId(), clock.instant()));
        return startSession(user, client);
    }

    @Transactional
    public AuthResult login(LoginRequest request, ClientInfo client) {
        User user = users.findByEmail(User.normalizeEmail(request.email())).orElse(null);
        String hash = user != null ? user.getPasswordHash() : dummyPasswordHash;
        boolean matches = passwordEncoder.matches(request.password(), hash);
        if (user == null || !matches || !user.isEnabled()) {
            throw new ApplicationException(ErrorCode.INVALID_CREDENTIALS, "Invalid email or password");
        }
        events.publishEvent(new UserLoggedInEvent(user.getId(), clock.instant()));
        return startSession(user, client);
    }

    @Transactional
    public AuthResult refresh(String refreshToken) {
        IssuedSession session = sessions.rotate(refreshToken);
        User user = users.findById(session.userId())
                .filter(User::isEnabled)
                .orElseThrow(() -> new ApplicationException(ErrorCode.INVALID_REFRESH_TOKEN, "Session invalid"));
        return toResult(user, session);
    }

    @Transactional
    public void logout(String refreshToken) {
        sessions.revoke(refreshToken);
    }

    private AuthResult startSession(User user, ClientInfo client) {
        return toResult(user, sessions.open(user.getId(), client));
    }

    private AuthResult toResult(User user, IssuedSession session) {
        AccessToken token = accessTokens.issue(user);
        AuthResponse body = new AuthResponse(token.value(), "Bearer", token.expiresInSeconds(),
                UserResponse.from(user));
        return new AuthResult(body, session.refreshToken(), session.expiresAt());
    }
}
