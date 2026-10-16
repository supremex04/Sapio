package com.sapio;

import java.util.UUID;

import jakarta.servlet.http.Cookie;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Helpers to register users through the real API. */
public final class TestUsers {

    private TestUsers() {
    }

    public record Registered(String email, String password, String accessToken, Cookie refreshCookie) {
        public String bearer() {
            return "Bearer " + accessToken;
        }
    }

    public static Registered register(MockMvc mvc, JsonMapper json) throws Exception {
        return register(mvc, json, "STUDENT", "Test User");
    }

    public static Registered register(MockMvc mvc, JsonMapper json, String role, String fullName) throws Exception {
        String email = "user-" + UUID.randomUUID() + "@example.com";
        String password = "correct-horse-battery";
        MvcResult result = mvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"%s","password":"%s","fullName":"%s","role":"%s"}
                                """.formatted(email, password, fullName, role)))
                .andExpect(status().isCreated())
                .andReturn();
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return new Registered(email, password, body.get("accessToken").asString(),
                result.getResponse().getCookie("sapio_refresh"));
    }
}
