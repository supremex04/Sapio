package com.sapio.visualization;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import com.sapio.FakeAi;
import com.sapio.IntegrationTest;
import com.sapio.TestUsers;
import com.sapio.TestUsers.Registered;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** A visualization end to end: plan, code, a failed render repaired by the AI, the video, reuse and deletion. */
@IntegrationTest
class VisualizationFlowIT {

    private static final List<VisualizeAi.Beat> BEATS = List.of(new VisualizeAi.Beat("A right triangle", "Look"),
            new VisualizeAi.Beat("Squares on each side", "Areas add up"));

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @MockitoBean
    VisualizeAi ai;

    @MockitoBean
    RendererClient renderer;

    Registered learner;

    @BeforeEach
    void learner() throws Exception {
        learner = TestUsers.register(mvc, json);
        when(ai.storyboard(any())).thenAnswer(inv -> {
            VisualizeAi.StoryboardSpec spec = inv.getArgument(0);
            return spec.request().contains("love")
                    ? new VisualizeAi.Storyboard(false, "Feelings are not something we can animate well.", null, null,
                            List.of(), "fake")
                    : new VisualizeAi.Storyboard(true, null, "Pythagoras", "math", BEATS, "fake");
        });
        when(ai.code(any())).thenReturn(new VisualizeAi.SceneCode("broken", "fake"));
        when(ai.code(argThat(s -> s != null && s.error() != null))).thenReturn(new VisualizeAi.SceneCode("fixed", "fake"));
    }

    @Test
    void aFailedRenderIsRepairedAndTheVideoIsServed() throws Exception {
        when(renderer.render(eq("broken"), any())).thenThrow(new RendererClient.RenderFailed("error", "NameError: Foo"));
        when(renderer.render(eq("fixed"), any())).thenReturn(new RendererClient.Rendered(new byte[] {1, 2, 3}, FakeAi.PNG, 4.5, true));

        String id = create("a^2 + b^2 = c^2").get("id").asString();
        JsonNode done = await(id);
        assertThat(done.get("status").asString()).isEqualTo("READY");
        assertThat(done.get("title").asString()).isEqualTo("Pythagoras");
        assertThat(done.get("beats")).hasSize(2);
        assertThat(done.get("code").asString()).isEqualTo("fixed");
        assertThat(done.get("attempts").asInt()).isEqualTo(2);
        assertThat(done.get("durationSeconds").asDouble()).isEqualTo(4.5);
        assertThat(done.get("narrated").asBoolean()).isTrue();
        // Each beat's caption is what the renderer speaks.
        verify(renderer).render(eq("fixed"), eq(List.of("Look", "Areas add up")));
        verify(ai).code(argThat(s -> s != null && "broken".equals(s.previousCode()) && "NameError: Foo".equals(s.error())));

        // The signed URL plays without a login; a tampered one does not.
        String video = done.get("videoUrl").asString();
        byte[] bytes = mvc.perform(get(video)).andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(bytes).containsExactly(1, 2, 3);
        mvc.perform(get(video.replace("sig=", "sig=x"))).andExpect(status().isNotFound());
        mvc.perform(get(done.get("thumbnailUrl").asString())).andExpect(status().isOk());

        // Asking again for the same thing (spacing and case aside) reuses it.
        assertThat(create("A^2 +  b^2 = c^2").get("id").asString()).isEqualTo(id);
        verify(ai, times(1)).storyboard(any());

        // Someone else cannot see it.
        Registered other = TestUsers.register(mvc, json);
        call(other, get("/api/visualizations/" + id), null).andExpect(status().isNotFound());

        call(learner, delete("/api/visualizations/" + id), null).andExpect(status().isNoContent());
        call(learner, get("/api/visualizations/" + id), null).andExpect(status().isNotFound());
        mvc.perform(get(video)).andExpect(status().isNotFound());
    }

    @Test
    void anInfeasibleRequestFailsWithTheReasonAndNeverRenders() throws Exception {
        JsonNode done = await(create("what is love").get("id").asString());
        assertThat(done.get("status").asString()).isEqualTo("FAILED");
        assertThat(done.get("error").asString()).contains("Feelings");
        verify(renderer, never()).render(any(), any());
    }

    @Test
    void codeThatNeverRendersFailsAfterTheLastAttemptAndCanBeRetried() throws Exception {
        when(renderer.render(any(), any())).thenThrow(new RendererClient.RenderFailed("error", "boom"));
        String id = create("sine wave").get("id").asString();
        JsonNode done = await(id);
        assertThat(done.get("status").asString()).isEqualTo("FAILED");
        assertThat(done.get("attempts").asInt()).isEqualTo(3);
        assertThat(done.get("error").asString()).contains("3 tries");

        doReturn(new RendererClient.Rendered(new byte[] {9}, new byte[0], 2, false)).when(renderer).render(eq("fixed"), any());
        call(learner, post("/api/visualizations/" + id + "/retry"), null).andExpect(status().isOk());
        JsonNode again = await(id);
        assertThat(again.get("status").asString()).isEqualTo("READY");
        assertThat(again.get("thumbnailUrl").isNull()).isTrue();
        assertThat(again.get("narrated").asBoolean()).isFalse();
        assertThat(getJson("/api/visualizations").findValuesAsString("id")).containsExactly(id);
    }

    @Test
    void theRendererBeingDownIsNotBlamedOnTheCode() throws Exception {
        when(renderer.render(any(), any())).thenThrow(new RendererClient.RenderFailed("unavailable", "down"));
        JsonNode done = await(create("a circle").get("id").asString());
        assertThat(done.get("status").asString()).isEqualTo("FAILED");
        assertThat(done.get("error").asString()).contains("unavailable");
        verify(ai, times(1)).code(any());
    }

    @Test
    void anEmptyRequestIsRejected() throws Exception {
        call(learner, post("/api/visualizations"), Map.of("request", " ")).andExpect(status().isBadRequest());
    }

    private JsonNode create(String request) throws Exception {
        return json.readTree(call(learner, post("/api/visualizations"), Map.of("request", request))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode await(String id) throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
        while (true) {
            JsonNode v = getJson("/api/visualizations/" + id);
            String s = v.get("status").asString();
            if (s.equals("READY") || s.equals("FAILED") || Instant.now().isAfter(deadline)) {
                return v;
            }
            Thread.sleep(50);
        }
    }

    private ResultActions call(Registered user, MockHttpServletRequestBuilder request, Object body) throws Exception {
        request.header("Authorization", user.bearer());
        if (body != null) {
            request.contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        }
        return mvc.perform(request);
    }

    private JsonNode getJson(String path) throws Exception {
        return json.readTree(mvc.perform(get(path).header("Authorization", learner.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
