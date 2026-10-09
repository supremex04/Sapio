package com.ved.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import com.ved.auth.AuthService.AuthResult;
import com.ved.auth.session.ClientInfo;
import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
class AuthController {

    private final AuthService authService;
    private final RefreshTokenCookies cookies;

    AuthController(AuthService authService, RefreshTokenCookies cookies) {
        this.authService = authService;
        this.cookies = cookies;
    }

    @PostMapping("/register")
    ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest body, HttpServletRequest request) {
        return withSession(HttpStatus.CREATED, authService.register(body, clientInfo(request)));
    }

    @PostMapping("/login")
    ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest body, HttpServletRequest request) {
        return withSession(HttpStatus.OK, authService.login(body, clientInfo(request)));
    }

    @PostMapping("/refresh")
    ResponseEntity<AuthResponse> refresh(HttpServletRequest request) {
        String token = cookies.read(request).orElseThrow(
                () -> new ApplicationException(ErrorCode.INVALID_REFRESH_TOKEN, "No active session"));
        return withSession(HttpStatus.OK, authService.refresh(token));
    }

    @PostMapping("/logout")
    ResponseEntity<Void> logout(HttpServletRequest request) {
        cookies.read(request).ifPresent(authService::logout);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    private ResponseEntity<AuthResponse> withSession(HttpStatus status, AuthResult result) {
        return ResponseEntity.status(status)
                .header(HttpHeaders.SET_COOKIE,
                        cookies.create(result.refreshToken(), result.refreshTokenExpiresAt()).toString())
                .body(result.response());
    }

    private static ClientInfo clientInfo(HttpServletRequest request) {
        return new ClientInfo(request.getHeader(HttpHeaders.USER_AGENT), request.getRemoteAddr());
    }
}
