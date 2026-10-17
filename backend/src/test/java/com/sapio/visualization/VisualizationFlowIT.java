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

    private static final List<VisualizeAi.Beat> BEATS = List.of(new VisualizeAi.Beat("A right triangle", "Look", ""),
            new VisualizeAi.Beat("Squares on each side", "Areas add up", ""));
    private static final List<VisualizeAi.Beat> FOCUSED = List.of(new VisualizeAi.Beat("The whole thing", "Look", ""),
            new VisualizeAi.Beat("One part", "This part", "C(=O)[OX2H1]"));

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @MockitoBean
    VisualizeAi ai;

    @MockitoBean
    RendererClient renderer;

    @MockitoBean
    ProteinBank proteinBank;

    Registered learner;

    @BeforeEach
    void learner() throws Exception {
        learner = TestUsers.register(mvc, json);
        when(ai.storyboard(any())).thenAnswer(inv -> {
            VisualizeAi.StoryboardSpec spec = inv.getArgument(0);
            if (spec.request().contains("love")) {
                return new VisualizeAi.Storyboard(false, "Feelings are not something we can animate well.", null,
                        null, "animation", null, null, "", List.of(), "fake");
            }
            String engine = spec.engine().equals("auto") ? guess(spec.request()) : spec.engine();
            return switch (engine) {
                case "molecule" -> new VisualizeAi.Storyboard(true, null, "Aspirin", "chemistry", "molecule",
                        new VisualizeAi.Molecule("aspirin", "CC(=O)Oc1ccccc1C(=O)O"), null, "", FOCUSED, "fake");
                case "protein" -> new VisualizeAi.Storyboard(true, null, "Haemoglobin", "biology", "protein", null,
                        new VisualizeAi.Protein("4HHB", "human haemoglobin"), "", FOCUSED, "fake");
                case "code" -> new VisualizeAi.Storyboard(true, null, "Factorial", "code", "code", null, null,
                        "print('ai program')\n", FOCUSED, "fake");
                default -> new VisualizeAi.Storyboard(true, null, "Pythagoras", "math", "animation", null, null, "",
                        BEATS, "fake");
            };
        });
        when(renderer.narrate(any())).thenReturn(new RendererClient.Narration(new byte[] {7, 7},
                List.of(new RendererClient.Timing(0, 1.5), new RendererClient.Timing(1.5, 2.0))));
        when(ai.code(any())).thenReturn(new VisualizeAi.SceneCode("broken", "fake"));
        when(ai.code(argThat(s -> s != null && s.error() != null))).thenReturn(new VisualizeAi.SceneCode("fixed", "fake"));
    }

    @Test
    void aFailedRenderIsRepairedAndTheVideoIsServed() throws Exception {
        when(renderer.render(eq("broken"), any())).thenThrow(new RendererClient.RenderFailed("error", "NameError: Foo"));
        when(renderer.render(eq("fixed"), any())).thenReturn(new RendererClient.Rendered(new byte[] {1, 2, 3}, FakeAi.PNG, 4.5, true, List.of()));

        String id = create("a^2 + b^2 = c^2").get("id").asString();
        JsonNode done = await(id);
        assertThat(done.get("status").asString()).isEqualTo("READY");
        assertThat(done.get("title").asString()).isEqualTo("Pythagoras");
        assertThat(done.get("beats")).hasSize(2);
        assertThat(done.has("code")).isFalse(); // the scene code stays on the server
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
    void layoutProblemsGoBackToTheAiAndTheCleanestVideoIsKept() throws Exception {
        List<String> problems = List.of("Step 2: the GREEN ParametricFunction spans x -4.4 to 9.1: part of it is "
                + "outside the frame.");
        when(renderer.render(eq("broken"), any())).thenReturn(
                new RendererClient.Rendered(new byte[] {5}, FakeAi.PNG, 4, true, problems));
        when(renderer.render(eq("fixed"), any())).thenReturn(
                new RendererClient.Rendered(new byte[] {6}, FakeAi.PNG, 4, true, List.of()));

        JsonNode done = await(create("a cosine curve").get("id").asString());
        assertThat(done.get("status").asString()).isEqualTo("READY");
        verify(ai).code(argThat(s -> s != null && "broken".equals(s.previousCode()) && s.error() != null
                && s.error().contains("layout problems") && s.error().contains("outside the frame")));
        byte[] video = mvc.perform(get(done.get("videoUrl").asString())).andReturn().getResponse()
                .getContentAsByteArray();
        assertThat(video).containsExactly(6); // the repaired one
    }

    @Test
    void aRepairThatDoesNotRenderKeepsTheVideoThatDid() throws Exception {
        when(renderer.render(eq("broken"), any())).thenReturn(new RendererClient.Rendered(new byte[] {5}, FakeAi.PNG,
                4, true, List.of("Step 1: the RED Square reaches down to y -3.4, into the subtitle area.")));
        when(renderer.render(eq("fixed"), any())).thenThrow(new RendererClient.RenderFailed("error", "NameError"));

        JsonNode done = await(create("a red square").get("id").asString());
        assertThat(done.get("status").asString()).isEqualTo("READY");
        byte[] video = mvc.perform(get(done.get("videoUrl").asString())).andReturn().getResponse()
                .getContentAsByteArray();
        assertThat(video).containsExactly(5); // a usable video is never thrown away
        assertThat(done.get("attempts").asInt()).isEqualTo(3); // three renders made; the first one kept
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

        doReturn(new RendererClient.Rendered(new byte[] {9}, new byte[0], 2, false, List.of())).when(renderer).render(eq("fixed"), any());
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
    void aMoleculeIsBuiltByRdkitWithoutSceneCodeAndNarratedAsATrack() throws Exception {
        when(renderer.molecule(any(), any(), any())).thenReturn(molecule());

        JsonNode done = await(create("aspirin").get("id").asString());
        assertThat(done.get("status").asString()).isEqualTo("READY");
        assertThat(done.get("engine").asString()).isEqualTo("MOLECULE");
        assertThat(done.at("/scene/formula").asString()).isEqualTo("C9H8O4");
        assertThat(done.at("/scene/highlights/1/0").asInt()).isEqualTo(10);
        assertThat(done.at("/scene/drawings/1").asString()).isEqualTo("aGlnaGxpZ2h0ZWQ=");
        assertThat(done.at("/scene/timing/1/start").asDouble()).isEqualTo(1.5);
        assertThat(done.get("durationSeconds").asDouble()).isEqualTo(3.5);
        assertThat(done.get("narrated").asBoolean()).isTrue();
        // Each beat's focus is what RDKit highlights; no scene code, no render.
        verify(renderer).molecule(eq("CC(=O)Oc1ccccc1C(=O)O"), eq(List.of("", "C(=O)[OX2H1]")),
                eq(List.of("Look", "This part")));
        verify(ai, never()).code(any());
        verify(renderer, never()).render(any(), any());

        byte[] audio = mvc.perform(get(done.get("audioUrl").asString())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(audio).containsExactly(7, 7);
        mvc.perform(get(done.get("thumbnailUrl").asString())).andExpect(status().isOk());
        // The list stays light: no scenes in it.
        assertThat(getJson("/api/visualizations").get(0).get("scene").isNull()).isTrue();
    }

    @Test
    void aSmilesStringIsRoutedByTheRulesOnlyOnceRdkitReadsIt() throws Exception {
        when(renderer.molecule(any(), any(), any())).thenReturn(molecule());
        when(renderer.molecule(eq("E=mc2"), any(), any())).thenThrow(new RendererClient.RenderFailed("input", "not SMILES"));

        await(create("CC(=O)Oc1ccccc1C(=O)O").get("id").asString());
        verify(ai).storyboard(argThat(s -> s != null && s.engine().equals("molecule")));

        await(create("E=mc2").get("id").asString());
        verify(ai).storyboard(argThat(s -> s != null && s.request().equals("E=mc2") && s.engine().equals("auto")));
    }

    @Test
    void aProteinComesFromTheProteinDataBankAndItsStructureIsServed() throws Exception {
        byte[] cif;
        try (var in = getClass().getResourceAsStream("/visualization/haemoglobin-mini.cif")) {
            cif = in.readAllBytes();
        }
        when(proteinBank.fetch("4HHB")).thenReturn(new ProteinBank.Entry("4HHB", "THE CRYSTAL STRUCTURE OF HUMAN "
                + "DEOXYHAEMOGLOBIN", cif, FakeAi.PNG));
        when(proteinBank.fetch("9ZZZ")).thenThrow(new ProteinBank.NotFound("There is no entry 9ZZZ"));

        JsonNode done = await(create("haemoglobin", "PROTEIN").get("id").asString());
        assertThat(done.get("status").asString()).isEqualTo("READY");
        assertThat(done.at("/scene/pdbId").asString()).isEqualTo("4HHB");
        assertThat(done.at("/scene/entryTitle").asString()).contains("HAEMOGLOBIN");
        String served = mvc.perform(get(done.get("structureUrl").asString())).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        assertThat(served).startsWith("data_MINI");
        // Foci are checked against the structure: the storyboard's SMARTS-like focus is no residue, so it is dropped.
        assertThat(done.at("/scene/focus/1").asString()).isEmpty();
        assertThat(done.at("/scene/chains/1/description").asString()).isEqualTo("Hemoglobin subunit beta");

        // The learner's own ID comes first; when it does not exist the AI's is used.
        JsonNode mine = await(create("show 9ZZZ protein").get("id").asString());
        assertThat(mine.at("/scene/pdbId").asString()).isEqualTo("4HHB");
    }

    @Test
    void pastedCodeIsTracedAsGivenAndTheAisProgramIsTheFallback() throws Exception {
        JsonNode trace = json.readTree("{\"steps\":[{\"line\":1}],\"output\":\"6\\n\",\"error\":null}");
        String program = "def f(n):\n    return n * 2\nprint(f(3))\n";
        when(renderer.trace(eq(program), any())).thenReturn(trace);
        when(renderer.trace(eq("print('ai program')\n"), any())).thenReturn(trace);

        JsonNode done = await(create("```python\n" + program + "```").get("id").asString());
        assertThat(done.get("engine").asString()).isEqualTo("CODE");
        assertThat(done.at("/scene/program").asString()).isEqualTo(program);
        assertThat(done.at("/scene/trace/output").asString()).isEqualTo("6\n");
        assertThat(done.at("/scene/lines/0").asString()).isEmpty(); // "" focus: no lines
        assertThat(done.at("/scene/lines/1").asString()).isEmpty(); // a SMARTS is no line of this program

        String java = "int x = 2;\nx = x * 3;\nSystem.out.println(x);";
        when(renderer.trace(eq(java + "\n"), any())).thenThrow(new RendererClient.RenderFailed("code", "Syntax error"));
        JsonNode translated = await(create(java).get("id").asString());
        assertThat(translated.at("/scene/program").asString()).isEqualTo("print('ai program')\n");
    }

    @Test
    void anEngineTheLearnerChoseFailsWithTheReasonInsteadOfFallingBack() throws Exception {
        when(renderer.molecule(any(), any(), any())).thenThrow(new RendererClient.RenderFailed("input", "not a valid SMILES"));
        JsonNode done = await(create("aspirin", "MOLECULE").get("id").asString());
        assertThat(done.get("status").asString()).isEqualTo("FAILED");
        assertThat(done.get("error").asString()).contains("not a valid SMILES");
    }

    @Test
    void anEmptyRequestIsRejected() throws Exception {
        call(learner, post("/api/visualizations"), Map.of("request", " ")).andExpect(status().isBadRequest());
    }

    private JsonNode create(String request) throws Exception {
        return create(request, "AUTO");
    }

    private JsonNode create(String request, String engine) throws Exception {
        return json.readTree(call(learner, post("/api/visualizations"), Map.of("request", request, "engine", engine))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private static String guess(String request) {
        return request.contains("aspirin") ? "molecule" : request.contains("haemoglobin") ? "protein" : "animation";
    }

    private static RendererClient.Molecule molecule() {
        return new RendererClient.Molecule("CC(=O)Oc1ccccc1C(=O)O", "C9H8O4", 180.16, 13, "mol block", true,
                java.util.Base64.getEncoder().encodeToString(FakeAi.PNG), List.of(List.of(), List.of(10, 11, 12)),
                List.of("cGxhaW4=", "aGlnaGxpZ2h0ZWQ="));
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
