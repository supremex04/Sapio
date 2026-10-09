package com.ved.document;

import java.time.Duration;
import java.util.UUID;

import com.ved.IntegrationTest;
import com.ved.TestUsers;
import com.ved.TestUsers.Registered;
import com.ved.document.ocr.OcrException;
import com.ved.document.ocr.OcrGateway;
import com.ved.document.ocr.OcrResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@IntegrationTest
class DocumentFlowIT {

    private static final byte[] PNG_BYTES = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0};

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @MockitoBean
    OcrGateway ocrGateway;

    @Test
    void uploadIsProcessedAsynchronouslyAndResultCanBeFetched() throws Exception {
        when(ocrGateway.extract(any())).thenReturn(new OcrResult("# Hello\n\nWorld", "fake", 1));
        Registered user = TestUsers.register(mvc, json);

        UUID id = upload(user, "scan.png", PNG_BYTES);
        JsonNode doc = awaitTerminal(user, id);

        assertThat(doc.get("status").asString()).isEqualTo("COMPLETED");
        assertThat(doc.get("engine").asString()).isEqualTo("fake");
        mvc.perform(get("/api/documents/{id}/result", id).header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").value("# Hello\n\nWorld"));
        mvc.perform(get("/api/documents/{id}/result/download", id).header("Authorization", user.bearer()))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("scan.md")))
                .andExpect(content().string("# Hello\n\nWorld"));
        mvc.perform(get("/api/documents").header("Authorization", user.bearer()))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].filename").value("scan.png"));
    }

    @Test
    void ocrFailureIsRecordedOnTheDocument() throws Exception {
        when(ocrGateway.extract(argThat(r -> r.filename().equals("bad.png"))))
                .thenThrow(new OcrException("Document could not be processed: corrupt image"));
        Registered user = TestUsers.register(mvc, json);

        UUID id = upload(user, "bad.png", PNG_BYTES);
        JsonNode doc = awaitTerminal(user, id);

        assertThat(doc.get("status").asString()).isEqualTo("FAILED");
        assertThat(doc.get("errorMessage").asString()).contains("corrupt image");
        mvc.perform(get("/api/documents/{id}/result", id).header("Authorization", user.bearer()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("RESULT_NOT_READY"));
    }

    @Test
    void fileWhoseContentDoesNotMatchItsExtensionIsRejected() throws Exception {
        Registered user = TestUsers.register(mvc, json);
        mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", "evil.pdf", "application/pdf", "MZ\u0090binary".getBytes()))
                        .header("Authorization", user.bearer()))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_FILE_TYPE"));
    }

    @Test
    void usersCannotSeeOrDeleteEachOthersDocuments() throws Exception {
        when(ocrGateway.extract(any())).thenReturn(new OcrResult("text", "fake", 1));
        Registered owner = TestUsers.register(mvc, json);
        Registered other = TestUsers.register(mvc, json);
        UUID id = upload(owner, "mine.png", PNG_BYTES);

        mvc.perform(get("/api/documents/{id}", id).header("Authorization", other.bearer()))
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

    private UUID upload(Registered user, String filename, byte[] bytes) throws Exception {
        String body = mvc.perform(multipart("/api/documents")
                        .file(new MockMultipartFile("file", filename, "application/octet-stream", bytes))
                        .header("Authorization", user.bearer()))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andReturn().getResponse().getContentAsString();
        return UUID.fromString(json.readTree(body).get("id").asString());
    }

    private JsonNode awaitTerminal(Registered user, UUID id) throws Exception {
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
