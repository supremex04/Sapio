package com.ved.document;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import com.ved.FakeAi;
import com.ved.IntegrationTest;
import com.ved.MutableClock;
import com.ved.TestUsers;
import com.ved.TestUsers.Registered;
import com.ved.document.extraction.ExtractionException;
import com.ved.document.extraction.ExtractionGateway;
import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.study.ai.StudyAi;
import com.ved.study.ai.StudyAiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
public class DocumentFlowIT {

    public static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @MockitoBean
    ExtractionGateway extraction;

    @MockitoBean
    StudyAi ai;

    @MockitoBean
    KnowledgeAi knowledgeAi;

    @Autowired
    MutableClock clock;

    @Autowired
    JdbcTemplate jdbc;

    @BeforeEach
    void stubAi() {
        FakeAi.stub(knowledgeAi, ai);
    }

    @Test
    void uploadIsExtractedAnalysedAndReady() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        Registered user = TestUsers.register(mvc, json);

        UUID id = upload(mvc, json, user, "scan.png", PNG_BYTES);
        JsonNode doc = awaitTerminal(mvc, json, user, id);

        assertThat(doc.get("status").asString()).isEqualTo("READY");
        assertThat(doc.get("title").asString()).isEqualTo("Intro Biology");
        assertThat(doc.get("textPages").asInt()).isEqualTo(3);
        mvc.perform(get("/api/documents/{id}/result", id).header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("# The Cell\n\n..."));
        mvc.perform(get("/api/documents/{id}/result/download", id).header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("scan.md")))
                .andExpect(content().string("# The Cell\n\n..."));
        mvc.perform(get("/api/documents/{id}/material", id).header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.topics.length()").value(2));
    }

    @Test
    void extractionFailureIsRecorded() throws Exception {
        when(extraction.extract(argThat(r -> r.filename().equals("bad.png"))))
                .thenThrow(new ExtractionException("Document could not be processed: corrupt image"));
        Registered user = TestUsers.register(mvc, json);

        UUID id = upload(mvc, json, user, "bad.png", PNG_BYTES);
        JsonNode doc = awaitTerminal(mvc, json, user, id);

        assertThat(doc.get("status").asString()).isEqualTo("FAILED");
        assertThat(doc.get("errorMessage").asString()).contains("corrupt image");
        mvc.perform(get("/api/documents/{id}/result", id).header("Authorization", user.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));
    }

    @Test
    void analysisFailureCanBeRetriedWithoutReExtracting() throws Exception {
        when(extraction.extract(argThat(r -> r.filename().equals("retry.png")))).thenReturn(FakeAi.extraction());
        doThrow(new StudyAiException("The AI service is busy", false))
                .doAnswer(inv -> FakeAi.material(inv.getArgument(0)))
                .when(ai).buildMaterial(argThat(s -> s != null && s.filename().equals("retry.png")));
        Registered user = TestUsers.register(mvc, json);

        UUID id = upload(mvc, json, user, "retry.png", PNG_BYTES);
        JsonNode failed = awaitTerminal(mvc, json, user, id);
        assertThat(failed.get("status").asString()).isEqualTo("FAILED");
        assertThat(failed.get("errorMessage").asString()).contains("AI service is busy");
        // The extracted text is already available even though analysis failed.
        mvc.perform(get("/api/documents/{id}/result", id).header("Authorization", user.bearer()))
                .andExpect(status().isOk());

        mvc.perform(post("/api/documents/{id}/retry", id).header("Authorization", user.bearer()))
                .andExpect(status().isAccepted());
        assertThat(awaitTerminal(mvc, json, user, id).get("status").asString()).isEqualTo("READY");
        verify(extraction, times(1)).extract(argThat(r -> r.filename().equals("retry.png")));

        mvc.perform(post("/api/documents/{id}/retry", id).header("Authorization", user.bearer()))
                .andExpect(status().isConflict());
    }

    @Test
    void transientFailuresRetryAutomaticallyAndResume() throws Exception {
        when(extraction.extract(argThat(r -> r != null && r.filename().equals("busy.png"))))
                .thenThrow(new ExtractionException("Text extraction is unavailable right now", null, true))
                .thenReturn(FakeAi.extraction());
        Registered user = TestUsers.register(mvc, json);

        UUID id = upload(mvc, json, user, "busy.png", PNG_BYTES);
        JsonNode paused = awaitTerminal(mvc, json, user, id);
        assertThat(paused.get("status").asString()).isEqualTo("FAILED");
        assertThat(paused.get("errorMessage").asString()).contains("continue automatically");
        assertThat(paused.get("retryAt").isNull()).isFalse();

        clock.advance(Duration.ofMinutes(2)); // the scheduler (polling every 200 ms in tests) picks it up
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String status = "FAILED";
        while (!"READY".equals(status) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            status = json.readTree(mvc.perform(get("/api/documents/{id}", id).header("Authorization", user.bearer()))
                    .andReturn().getResponse().getContentAsString()).get("status").asString();
        }
        assertThat(status).isEqualTo("READY");
    }

    @Test
    void aQuotaResetHintPostponesTheAutomaticRetry() throws Exception {
        when(extraction.extract(argThat(r -> r != null && r.filename().equals("quota.png"))))
                .thenThrow(new ExtractionException("The AI service is out of quota", null, true, Duration.ofHours(3)));
        Registered user = TestUsers.register(mvc, json);

        UUID id = upload(mvc, json, user, "quota.png", PNG_BYTES);
        JsonNode paused = awaitTerminal(mvc, json, user, id);

        // Not after the first configured delay (1 min), which would only burn a retry.
        assertThat(Instant.parse(paused.get("retryAt").asString()))
                .isAfterOrEqualTo(clock.instant().plus(Duration.ofHours(3)).minusSeconds(5));
    }

    @Test
    void documentsAbandonedByAStoppedInstanceAreResumedButLiveOnesAreLeftAlone() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        Registered user = TestUsers.register(mvc, json);
        UUID abandoned = upload(mvc, json, user, "abandoned.png", PNG_BYTES);
        UUID live = upload(mvc, json, user, "live.png", PNG_BYTES);
        awaitTerminal(mvc, json, user, abandoned);
        awaitTerminal(mvc, json, user, live);

        // Simulate two instances mid-indexing: one stopped heartbeating an hour ago, one is still alive.
        Instant now = clock.instant();
        jdbc.update("update documents set status = 'INDEXING', heartbeat_at = ? where id = ?",
                Timestamp.from(now.minus(Duration.ofHours(1))), abandoned);
        jdbc.update("update documents set status = 'INDEXING', heartbeat_at = ? where id = ?",
                Timestamp.from(now), live);

        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        String status = "INDEXING";
        while (!"READY".equals(status) && System.nanoTime() < deadline) {
            Thread.sleep(100);
            status = jdbc.queryForObject("select status from documents where id = ?", String.class, abandoned);
        }
        assertThat(status).isEqualTo("READY");
        assertThat(jdbc.queryForObject("select status from documents where id = ?", String.class, live))
                .isEqualTo("INDEXING");
    }

    @Test
    void fileWhoseContentDoesNotMatchItsExtensionIsRejected() throws Exception {
        Registered user = TestUsers.register(mvc, json);
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "evil.pdf", "application/pdf", "MZ\u0090binary".getBytes()))
                        .header("Authorization", user.bearer()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
        verify(extraction, never()).extract(argThat(r -> r.filename().equals("evil.pdf")));
    }

    @Test
    void usersCannotSeeOrDeleteEachOthersDocuments() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        Registered owner = TestUsers.register(mvc, json);
        Registered other = TestUsers.register(mvc, json);
        UUID id = upload(mvc, json, owner, "mine.png", PNG_BYTES);
        awaitTerminal(mvc, json, owner, id);

        mvc.perform(get("/api/documents/{id}", id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/{id}/material", id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/{id}", id).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());

        mvc.perform(delete("/api/documents/{id}", id).header("Authorization", owner.bearer()))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/documents/{id}", id).header("Authorization", owner.bearer()))
                .andExpect(status().isNotFound());
    }

    @Test
    void uploadRequiresAuthentication() throws Exception {
        mvc.perform(multipart("/api/documents").file(new MockMultipartFile("file", "a.png", "image/png", PNG_BYTES)))
                .andExpect(status().isUnauthorized());
    }

    public static UUID upload(MockMvc mvc, JsonMapper json, Registered user, String filename, byte[] bytes)
            throws Exception {
        String body = mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                        .header("Authorization", user.bearer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asString());
    }

    public static JsonNode awaitTerminal(MockMvc mvc, JsonMapper json, Registered user, UUID id) throws Exception {
        long deadline = System.nanoTime() + Duration.ofSeconds(10).toNanos();
        while (System.nanoTime() < deadline) {
            String body = mvc.perform(get("/api/documents/{id}", id).header("Authorization", user.bearer()))
                    .andExpect(status().isOk())
                    .andReturn().getResponse().getContentAsString();
            JsonNode doc = json.readTree(body);
            if (DocumentStatus.valueOf(doc.get("status").asString()).isTerminal()) {
                return doc;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Document " + id + " did not finish processing in time");
    }
}
