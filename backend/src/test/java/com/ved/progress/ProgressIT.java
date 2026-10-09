package com.ved.progress;

import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.ved.IntegrationTest;
import com.ved.MutableClock;
import com.ved.TestUsers;
import com.ved.TestUsers.Registered;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class ProgressIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    MutableClock clock;

    @Test
    void heartbeatsCreditCappedActiveTimeAndReachTheDailyGoal() throws Exception {
        // Mid-day in Asia/Kathmandu, so the ~7 simulated minutes never cross local midnight. Pinned to
        // the future (not the past): tokens are issued with this clock but validated in real time.
        clock.set(LocalDate.now(ZoneOffset.UTC).plusDays(1).atTime(6, 0).toInstant(ZoneOffset.UTC));
        Registered user = TestUsers.register(mvc, json);
        mvc.perform(put("/api/progress/goal").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyMinutes\":5}"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.dailyMinutes").value(5));
        mvc.perform(put("/api/progress/goal").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"dailyMinutes\":7}"))
                .andExpect(status().isBadRequest());

        beat(user); // first beat starts a session: no credit
        for (int i = 0; i < 10; i++) {
            clock.advance(Duration.ofSeconds(30));
            beat(user);
        }
        clock.advance(Duration.ofSeconds(31)); // credit capped at 30 s
        beat(user);
        clock.advance(Duration.ofMinutes(10)); // a break: no credit for the gap
        beat(user);

        JsonNode summary = summary(user);
        assertThat(summary.at("/today/activeSeconds").asInt()).isEqualTo(330);
        assertThat(summary.at("/today/logins").asInt()).isEqualTo(1); // registration counts as a login
        assertThat(summary.at("/goal/reached").asBoolean()).isTrue();
        assertThat(summary.at("/xp/total").asLong()).isEqualTo(20); // daily goal bonus, once
        assertThat(summary.at("/streak/current").asInt()).isEqualTo(1);

        mvc.perform(get("/api/progress/daily?days=7").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[6].activeSeconds").value(330))
                .andExpect(jsonPath("$.days[0].activeSeconds").value(0));
        mvc.perform(get("/api/progress/activity").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.items[0].type").value("DAILY_GOAL_REACHED"))
                .andExpect(jsonPath("$.items[0].xp").value(20));
    }

    @Test
    void invalidTimeZonesAreIgnored() throws Exception {
        Registered user = TestUsers.register(mvc, json);
        mvc.perform(post("/api/progress/heartbeat").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"Mars/Olympus\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/users/me").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.timeZone").value("UTC"));
    }

    private void beat(Registered user) throws Exception {
        mvc.perform(post("/api/progress/heartbeat").header("Authorization", user.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"timeZone\":\"Asia/Kathmandu\"}"))
                .andExpect(status().isNoContent());
    }

    private JsonNode summary(Registered user) throws Exception {
        return json.readTree(mvc.perform(get("/api/progress/summary").header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
