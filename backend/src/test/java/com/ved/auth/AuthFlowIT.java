package com.ved.auth;

import jakarta.servlet.http.Cookie;

import com.ved.IntegrationTest;
import com.ved.TestUsers;
import com.ved.TestUsers.Registered;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.cookie;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class AuthFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Test
    void registerSetsHttpOnlyRefreshCookieAndReturnsUsableAccessToken() throws Exception {
        Registered user = TestUsers.register(mvc, json);

        assertThat(user.refreshCookie()).isNotNull();
        assertThat(user.refreshCookie().isHttpOnly()).isTrue();
        assertThat(user.refreshCookie().getPath()).isEqualTo("/api/auth");

        mvc.perform(get("/api/users/me").header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(user.email()))
                .andExpect(jsonPath("$.role").value("STUDENT"));
    }

    @Test
    void duplicateEmailIsRejected() throws Exception {
        Registered user = TestUsers.register(mvc, json);

        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"another-password","fullName":"X","role":"TEACHER"}
                        """.formatted(user.email().toUpperCase())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_REGISTERED"));
    }

    @Test
    void adminRoleCannotBeSelfAssigned() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"admin@example.com","password":"long-enough-pw","fullName":"X","role":"ADMIN"}
                        """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void invalidRegistrationReturnsFieldErrors() throws Exception {
        mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"not-an-email","password":"short","fullName":"","role":"STUDENT"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fieldErrors.length()").value(3));
    }

    @Test
    void loginWithWrongPasswordFails() throws Exception {
        Registered user = TestUsers.register(mvc, json);

        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"wrong-password"}
                        """.formatted(user.email())))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void loginRefreshRotationAndLogout() throws Exception {
        Registered user = TestUsers.register(mvc, json);

        MvcResult login = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email":"%s","password":"%s"}
                        """.formatted(user.email(), user.password())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andReturn();
        Cookie first = login.getResponse().getCookie("ved_refresh");

        // Refresh rotates the token...
        MvcResult refreshed = mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.user.email").value(user.email()))
                .andReturn();
        Cookie second = refreshed.getResponse().getCookie("ved_refresh");
        assertThat(second.getValue()).isNotEqualTo(first.getValue());

        // ...so the previous token no longer works.
        mvc.perform(post("/api/auth/refresh").cookie(first))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INVALID_REFRESH_TOKEN"));

        // Logout revokes the session and clears the cookie.
        mvc.perform(post("/api/auth/logout").cookie(second))
                .andExpect(status().isNoContent())
                .andExpect(cookie().maxAge("ved_refresh", 0));
        mvc.perform(post("/api/auth/refresh").cookie(second))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void protectedEndpointsRequireAValidToken() throws Exception {
        mvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("UNAUTHENTICATED"));
        mvc.perform(get("/api/users/me").header("Authorization", "Bearer not-a-jwt"))
                .andExpect(status().isUnauthorized());
    }
}
