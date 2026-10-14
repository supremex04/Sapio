package com.sapio.knowledge;

import java.util.List;
import java.util.UUID;

import com.sapio.FakeAi;
import com.sapio.IntegrationTest;
import com.sapio.TestUsers;
import com.sapio.TestUsers.Registered;
import com.sapio.document.DocumentFlowIT;
import com.sapio.document.extraction.ExtractionGateway;
import com.sapio.knowledge.ai.KnowledgeAi;
import com.sapio.knowledge.store.KnowledgeStore;
import com.sapio.study.ai.StudyAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Editing the reading view: minimal re-indexing, no eager AI work, conflicts and figures. */
@IntegrationTest
class SectionEditIT {

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
    KnowledgeStore store;

    Registered user;
    UUID documentId;
    String chapter;
    String osmosis;
    String figureId;

    @BeforeEach
    void readyDocument() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        FakeAi.stub(knowledgeAi, ai);
        when(ai.notes(any())).thenReturn(FakeAi.notes("## Osmosis notes"));
        user = TestUsers.register(mvc, json);
        documentId = DocumentFlowIT.upload(mvc, json, user, "bio.png", DocumentFlowIT.PNG_BYTES);
        assertThat(DocumentFlowIT.awaitTerminal(mvc, json, user, documentId).get("status").asString()).isEqualTo("READY");
        JsonNode sections = getJson("/api/documents/{d}/content", documentId).get("sections");
        chapter = sections.get(0).get("id").asString();
        osmosis = sections.get(1).get("id").asString();
        figureId = sections.get(1).at("/blocks/1/figureId").asString();
        assertThat(sections.get(1).get("revision").asInt()).isZero();
        assertThat(sections.get(1).get("editedAt").isNull()).isTrue();
    }

    @Test
    void aSmallEditIsSearchableAtOnceAndCostsNoAiWriting() throws Exception {
        getJson("/api/documents/{d}/nodes/{n}/notes", documentId, osmosis);
        clearInvocations(knowledgeAi, ai);

        JsonNode content = save(osmosis, "Osmosis", "Osmosis is the diffusion of water across a selectively permeable "
                + "membrane.\n\n" + figure(), 0).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString().transform(this::read);

        JsonNode section = content.get("sections").get(1);
        assertThat(section.get("revision").asInt()).isEqualTo(1);
        assertThat(section.get("editedAt").isNull()).isFalse();
        assertThat(section.at("/blocks/0/markdown").asString()).contains("selectively permeable");
        assertThat(section.at("/blocks/1/figureId").asString()).isEqualTo(figureId);
        // Keyword search sees the new wording immediately.
        assertThat(getJson("/api/documents/{d}/search?q=selectively", documentId).at("/hits/0/label").asString())
                .isEqualTo("1.1");

        // Only the changed chunk is embedded again; nothing AI-written is regenerated.
        awaitEmbedded();
        verify(knowledgeAi, never()).summarize(any(), any());
        verify(ai, never()).notes(any());
        JsonNode notes = getJson("/api/documents/{d}/nodes/{n}/notes", documentId, osmosis);
        assertThat(notes.get("stale").asBoolean()).isFalse();
        assertThat(getJson("/api/documents/{d}/nodes/{n}", documentId, osmosis).at("/node/summary").asString())
                .startsWith("Summary of");
    }

    @Test
    void aRewriteMarksNotesBehindUntilTheLearnerAsksForNewOnes() throws Exception {
        getJson("/api/documents/{d}/nodes/{n}/notes", documentId, osmosis);
        getJson("/api/documents/{d}/nodes/{n}/notes", documentId, chapter);
        String rewritten = "Water moves from dilute to concentrated solutions. ".repeat(12);
        save(osmosis, "Osmosis in plants", rewritten + "\n\n" + figure(), 0).andExpect(status().isOk());

        // The old notes are still shown, flagged as written before the change; no AI call yet.
        JsonNode stale = getJson("/api/documents/{d}/nodes/{n}/notes", documentId, osmosis);
        assertThat(stale.get("stale").asBoolean()).isTrue();
        assertThat(stale.get("markdown").asString()).startsWith("## Osmosis notes");
        verify(ai, times(2)).notes(any());

        // The chapter's notes cover the section too, so they are behind as well; an untouched chapter's are not.
        assertThat(getJson("/api/documents/{d}/nodes/{n}/notes", documentId, chapter).get("stale").asBoolean()).isTrue();
        String energy = getJson("/api/documents/{d}/content", documentId).at("/sections/2/id").asString();
        assertThat(getJson("/api/documents/{d}/nodes/{n}/notes", documentId, energy).get("stale").asBoolean()).isFalse();

        when(ai.notes(any())).thenReturn(FakeAi.notes("## Fresh notes"));
        JsonNode fresh = json.readTree(mvc.perform(post("/api/documents/{d}/nodes/{n}/notes/rewrite", documentId, osmosis)
                        .header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(fresh.get("stale").asBoolean()).isFalse();
        assertThat(fresh.get("markdown").asString()).startsWith("## Fresh notes");
    }

    @Test
    void smallEditsAddUpToASignificantChange() throws Exception {
        getJson("/api/documents/{d}/nodes/{n}/notes", documentId, osmosis);
        int revision = 0;
        String text = "Osmosis is the diffusion of water across a partially permeable membrane.";
        for (int i = 0; i < 12; i++) {
            text = text + " More detail " + i + " about water potential and turgor.";
            save(osmosis, "Osmosis", text + "\n\n" + figure(), revision++).andExpect(status().isOk());
        }
        assertThat(getJson("/api/documents/{d}/nodes/{n}/notes", documentId, osmosis).get("stale").asBoolean())
                .isTrue();
    }

    @Test
    void savesFromAnOldRevisionAreRejected() throws Exception {
        save(osmosis, "Osmosis", "First edit.", 0).andExpect(status().isOk());
        save(osmosis, "Osmosis", "Edit from a stale tab.", 0)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EDIT_CONFLICT"));
    }

    @Test
    void figuresCanBeRemovedButNotBorrowedFromOtherSections() throws Exception {
        save(osmosis, "Osmosis", "Text only now.", 0).andExpect(status().isOk());
        JsonNode tree = getJson("/api/documents/{d}/tree", documentId);
        assertThat(tree.at("/nodes/1/figureCount").asInt()).isZero();

        save(chapter, "The Cell", "Cells.\n\n![x](figure:" + UUID.randomUUID() + ")", 0)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("VALIDATION_FAILED"));
    }

    @Test
    void chunksStayInReadingOrderWhenAnEarlierSectionGrows() throws Exception {
        String longer = String.join("\n\n", List.of("A".repeat(800), "B".repeat(800), "C".repeat(800)));
        save(chapter, "The Cell", longer, 0).andExpect(status().isOk());

        List<KnowledgeStore.ChunkRow> chunks = store.chunksOfDocument(documentId);
        assertThat(chunks).extracting(KnowledgeStore.ChunkRow::ordinal).containsExactly(0, 1, 2, 3, 4);
        assertThat(chunks.get(0).content()).startsWith("A");
        assertThat(chunks.get(3).content()).startsWith("Osmosis");
        assertThat(chunks.get(4).content()).startsWith("Mitochondria");
    }

    @Test
    void onlyTheOwnerCanEdit() throws Exception {
        Registered other = TestUsers.register(mvc, json);
        mvc.perform(put("/api/documents/{d}/sections/{n}", documentId, osmosis).header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"x\",\"markdown\":\"x\",\"revision\":0}"))
                .andExpect(status().isNotFound());
    }

    private String figure() {
        return "![Figure 1.1: Osmosis in a plant cell](figure:" + figureId + ")";
    }

    private ResultActions save(String nodeId, String title, String markdown, int revision) throws Exception {
        String body = json.writeValueAsString(java.util.Map.of("title", title, "markdown", markdown, "revision", revision));
        return mvc.perform(put("/api/documents/{d}/sections/{n}", documentId, nodeId)
                .header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON).content(body));
    }

    private void awaitEmbedded() throws InterruptedException {
        for (int i = 0; i < 100 && store.hasUnembeddedChunks(documentId); i++) {
            Thread.sleep(50);
        }
        assertThat(store.hasUnembeddedChunks(documentId)).isFalse();
    }

    private JsonNode read(String body) {
        return json.readTree(body);
    }

    private JsonNode getJson(String path, Object... vars) throws Exception {
        return json.readTree(mvc.perform(get(path, vars).header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
