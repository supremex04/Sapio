package com.ved.sharing;

import java.util.List;
import java.util.UUID;

import com.ved.FakeAi;
import com.ved.IntegrationTest;
import com.ved.TestUsers;
import com.ved.TestUsers.Registered;
import com.ved.document.DocumentFlowIT;
import com.ved.document.extraction.ExtractionGateway;
import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.study.ai.StudyAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Share → Explore → import, and the guarantee that a copy and its original never affect each other. */
@IntegrationTest
class SharingFlowIT {

    @Autowired
    MockMvc mvc;

    @Autowired
    JsonMapper json;

    @Autowired
    JdbcTemplate jdbc;

    @MockitoBean
    ExtractionGateway extraction;

    @MockitoBean
    StudyAi ai;

    @MockitoBean
    KnowledgeAi knowledgeAi;

    Registered sharer;
    Registered learner;
    UUID original;

    @BeforeEach
    void sharedDocumentWithLessonAndNotes() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        FakeAi.stub(knowledgeAi, ai);
        when(ai.generateLesson(any())).thenAnswer(inv -> {
            StudyAi.LessonSpec spec = inv.getArgument(0);
            return spec == null ? null : FakeAi.lesson(spec.figures().getFirst().id());
        });
        when(ai.notes(any())).thenAnswer(inv -> {
            StudyAi.NotesSpec spec = inv.getArgument(0);
            if (spec == null) {
                return null;
            }
            String figure = spec.figures().getFirst().id();
            return new StudyAi.NotesDraft("## " + spec.style() + "\n\n![Osmosis](figure:" + figure + ")",
                    List.of(figure), List.of("§1.1"), "fake-model");
        });
        sharer = TestUsers.register(mvc, json);
        learner = TestUsers.register(mvc, json);
        original = DocumentFlowIT.upload(mvc, json, sharer, "bio.png", DocumentFlowIT.PNG_BYTES);
        assertThat(DocumentFlowIT.awaitTerminal(mvc, json, sharer, original).get("status").asString())
                .isEqualTo("READY");
        // The sharer studied a little: one lesson and Expand notes exist on the original.
        getJson(sharer, "/api/topics/{id}/lesson", firstTopic(sharer, original));
        getJson(sharer, "/api/documents/{d}/nodes/{n}/notes?style=EXPAND", original, section(sharer, original));
    }

    @Test
    void sharedDocumentsAppearInExploreAndImportAsIndependentCopies() throws Exception {
        String shareId = share(sharer, original, "biology", "Week 3 revision").get("id").asString();

        assertThat(getJson(sharer, "/api/explore/categories").get(0).get("slug").asString())
                .isEqualTo("computer-science");
        JsonNode card = getJson(learner, "/api/explore?category=biology&q=biology").get("items").get(0);
        assertThat(card.get("id").asString()).isEqualTo(shareId);
        assertThat(card.get("title").asString()).isEqualTo("Intro Biology");
        assertThat(card.at("/category/name").asString()).isEqualTo("Biology");
        assertThat(card.get("topicCount").asInt()).isEqualTo(2);
        assertThat(card.get("mine").asBoolean()).isFalse();
        assertThat(card.get("importedDocumentId").isNull()).isTrue();
        assertThat(getJson(sharer, "/api/explore").get("items").get(0).get("mine").asBoolean()).isTrue();
        assertThat(getJson(learner, "/api/explore?category=physics").get("items").size()).isZero();
        JsonNode detail = getJson(learner, "/api/explore/{id}", shareId);
        assertThat(detail.get("description").asString()).isEqualTo("Week 3 revision");
        assertThat(detail.get("topics").size()).isEqualTo(2);

        // Import: a ready copy at once, with the sharer's lesson and notes, and no AI calls.
        String body = mvc.perform(post("/api/explore/{id}/import", shareId).header("Authorization", learner.bearer()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.alreadyImported").value(false))
                .andReturn().getResponse().getContentAsString();
        UUID copy = UUID.fromString(json.readTree(body).get("documentId").asString());
        assertThat(copy).isNotEqualTo(original);
        JsonNode copyDoc = getJson(learner, "/api/documents/{id}", copy);
        assertThat(copyDoc.get("status").asString()).isEqualTo("READY");
        assertThat(copyDoc.at("/origin/shareId").asString()).isEqualTo(shareId);
        assertThat(copyDoc.at("/origin/sharedBy").asString()).isNotBlank();
        mvc.perform(post("/api/explore/{id}/import", shareId).header("Authorization", learner.bearer()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.alreadyImported").value(true))
                .andExpect(jsonPath("$.documentId").value(copy.toString()));
        card = getJson(learner, "/api/explore").get("items").get(0);
        assertThat(card.get("importCount").asInt()).isEqualTo(1);
        assertThat(card.get("importedDocumentId").asString()).isEqualTo(copy.toString());

        // Content is copied, learner state is not: the sharer's progress stays theirs.
        assertThat(getJson(learner, "/api/documents/{id}/material", copy).at("/topics/0/status").asString())
                .isEqualTo("NOT_STARTED");
        assertThat(getJson(sharer, "/api/documents/{id}/material", original).at("/topics/0/status").asString())
                .isEqualTo("IN_PROGRESS");

        // Same structure, all new ids; the lesson and notes point at the copy's own figures.
        assertThat(count("document_nodes", copy)).isEqualTo(count("document_nodes", original));
        assertThat(count("document_chunks", copy)).isEqualTo(count("document_chunks", original));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM document_chunks WHERE document_id = ? "
                + "AND embedding IS NOT NULL", Integer.class, copy)).isPositive();
        JsonNode lesson = getJson(learner, "/api/topics/{id}/lesson", firstTopic(learner, copy));
        verify(ai, times(1)).generateLesson(any()); // the copy reused the sharer's lesson
        String figureId = lesson.get("figures").propertyNames().iterator().next();
        assertThat(lesson.at("/sections/0/body").asString()).contains("figure:" + figureId);
        assertThat(jdbc.queryForObject("SELECT document_id FROM document_figures WHERE id = ?::uuid", UUID.class,
                figureId)).isEqualTo(copy);
        JsonNode notes = getJson(learner, "/api/documents/{d}/nodes/{n}/notes?style=EXPAND", copy, section(learner, copy));
        verify(ai, times(1)).notes(any());
        assertThat(notes.get("markdown").asString()).doesNotContain(originalFigure());

        // The learner's own work stays on their copy.
        getJson(learner, "/api/documents/{d}/nodes/{n}/notes?style=REVISION", copy, section(learner, copy));
        assertThat(notesOf(original)).containsExactly("EXPAND");
        assertThat(notesOf(copy)).containsExactlyInAnyOrder("EXPAND", "REVISION");

        // The original stays private, and deleting it leaves the copy whole (its own files).
        mvc.perform(get("/api/documents/{id}", original).header("Authorization", learner.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/documents/{id}", original).header("Authorization", sharer.bearer()))
                .andExpect(status().isNoContent());
        assertThat(getJson(learner, "/api/explore").get("items").size()).isZero(); // the share went with it
        JsonNode after = getJson(learner, "/api/documents/{id}", copy);
        assertThat(after.at("/origin/shareId").isNull()).isTrue();
        assertThat(after.at("/origin/sharedBy").asString()).isNotBlank();
        mvc.perform(get("/api/documents/{id}/original", copy).header("Authorization", learner.bearer()))
                .andExpect(status().isOk());
        String url = lesson.at("/figures/" + figureId + "/url").asString();
        mvc.perform(get(url)).andExpect(status().isOk()); // the copy's figure file survives
    }

    @Test
    void onlyOwnReadyUploadsCanBeSharedAndOwnSharesCannotBeImported() throws Exception {
        mvc.perform(put("/api/documents/{id}/share", original).header("Authorization", sharer.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"astrology\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(put("/api/documents/{id}/share", original).header("Authorization", learner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"biology\"}"))
                .andExpect(status().isNotFound());
        String shareId = share(sharer, original, "biology", "").get("id").asString();
        // Updating keeps the same share.
        assertThat(share(sharer, original, "medicine", "now in medicine").get("id").asString()).isEqualTo(shareId);
        assertThat(getJson(sharer, "/api/shares").get(0).at("/category/slug").asString()).isEqualTo("medicine");

        mvc.perform(post("/api/explore/{id}/import", shareId).header("Authorization", sharer.bearer()))
                .andExpect(status().isConflict());
        UUID copy = UUID.fromString(json.readTree(mvc.perform(post("/api/explore/{id}/import", shareId)
                        .header("Authorization", learner.bearer())).andReturn().getResponse().getContentAsString())
                .get("documentId").asString());
        mvc.perform(put("/api/documents/{id}/share", copy).header("Authorization", learner.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"category\":\"biology\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("INVALID_STATE"));

        mvc.perform(delete("/api/documents/{id}/share", original).header("Authorization", sharer.bearer()))
                .andExpect(status().isNoContent());
        assertThat(getJson(learner, "/api/explore").get("items").size()).isZero();
        mvc.perform(get("/api/explore/{id}", shareId).header("Authorization", learner.bearer()))
                .andExpect(status().isNotFound());
        assertThat(getJson(learner, "/api/documents/{id}", copy).get("status").asString()).isEqualTo("READY");
    }

    // ------------------------------------------------------------------------------------------------ helpers

    private JsonNode share(Registered who, UUID documentId, String category, String description) throws Exception {
        String body = mvc.perform(put("/api/documents/{id}/share", documentId).header("Authorization", who.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(new SharingResponses.ShareRequest(category, description))))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(body);
    }

    private UUID firstTopic(Registered who, UUID documentId) throws Exception {
        return UUID.fromString(getJson(who, "/api/documents/{id}/material", documentId).at("/topics/0/id").asString());
    }

    /** Section 1.1 (the one with the figure). */
    private String section(Registered who, UUID documentId) throws Exception {
        return getJson(who, "/api/documents/{id}/tree", documentId).get("nodes").get(1).get("id").asString();
    }

    private String originalFigure() {
        return jdbc.queryForObject("SELECT id::text FROM document_figures WHERE document_id = ? LIMIT 1", String.class,
                original);
    }

    private int count(String table, UUID documentId) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE document_id = ?", Integer.class,
                documentId);
    }

    private List<String> notesOf(UUID documentId) {
        return jdbc.queryForList("SELECT n.style FROM study_notes n JOIN document_nodes d ON d.id = n.node_id "
                + "WHERE d.document_id = ?", String.class, documentId);
    }

    private JsonNode getJson(Registered who, String path, Object... args) throws Exception {
        ResultActions result = mvc.perform(get(path, args).header("Authorization", who.bearer()));
        return json.readTree(result.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
