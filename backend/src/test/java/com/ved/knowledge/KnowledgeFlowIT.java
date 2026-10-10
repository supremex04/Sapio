package com.ved.knowledge;

import java.util.UUID;

import com.ved.FakeAi;
import com.ved.IntegrationTest;
import com.ved.TestUsers;
import com.ved.TestUsers.Registered;
import com.ved.document.DocumentFlowIT;
import com.ved.document.extraction.ExtractionGateway;
import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.knowledge.ai.KnowledgeAiException;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.study.ai.StudyAi;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Tree, reading view, hybrid search, term index, signed figures, notes and "ask your document". */
@IntegrationTest
class KnowledgeFlowIT {

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
    JsonNode tree;

    @BeforeEach
    void readyDocument() throws Exception {
        when(extraction.extract(any())).thenReturn(FakeAi.extraction());
        FakeAi.stub(knowledgeAi, ai);
        user = TestUsers.register(mvc, json);
        documentId = DocumentFlowIT.upload(mvc, json, user, "bio.png", DocumentFlowIT.PNG_BYTES);
        assertThat(DocumentFlowIT.awaitTerminal(mvc, json, user, documentId).get("status").asString()).isEqualTo("READY");
        tree = getJson("/api/documents/{id}/tree", documentId);
    }

    @Test
    void treeKeepsHierarchyLabelsAndFigureCounts() {
        JsonNode nodes = tree.get("nodes");
        assertThat(nodes.size()).isEqualTo(3);
        assertThat(nodes.get(0).get("label").asString()).isEqualTo("Chapter 1");
        assertThat(nodes.get(1).get("path").asString()).isEqualTo("1.1");
        assertThat(nodes.get(1).get("parentId").asString()).isEqualTo(nodes.get(0).get("id").asString());
        // f1 kept (educational); f2 (no caption, judged decorative) was removed during indexing.
        assertThat(nodes.get(1).get("figureCount").asInt()).isEqualTo(1);
        assertThat(nodes.get(2).get("figureCount").asInt()).isZero();
    }

    @Test
    void nodeViewInterleavesTextAndSignedFigures() throws Exception {
        String sectionId = node(1);
        JsonNode view = getJson("/api/documents/{d}/nodes/{n}", documentId, sectionId);
        assertThat(view.at("/node/summary").asString()).startsWith("Summary of");
        assertThat(view.at("/node/keyTerms/0").asString()).isEqualTo("osmosis");
        assertThat(view.at("/breadcrumb/0/label").asString()).isEqualTo("Chapter 1");
        assertThat(view.at("/blocks/0/type").asString()).isEqualTo("text");
        assertThat(view.at("/blocks/1/type").asString()).isEqualTo("figure");
        String figureId = view.at("/blocks/1/figureId").asString();
        String url = view.at("/figures/" + figureId + "/url").asString();
        assertThat(view.at("/figures/" + figureId + "/description").asString()).contains("membrane");

        // The signed URL works without auth; a tampered one does not.
        mvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(content().bytes(FakeAi.PNG));
        mvc.perform(get(url.replace("sig=", "sig=x"))).andExpect(status().isNotFound());
        mvc.perform(get("/api/figures/" + figureId + "/image?exp=1&sig=abc")).andExpect(status().isNotFound());
    }

    @Test
    void hybridSearchFindsExactTermsAndMeaningWithinScope() throws Exception {
        JsonNode keyword = getJson("/api/documents/{d}/search?q=osmosis", documentId);
        assertThat(keyword.at("/hits/0/label").asString()).isEqualTo("1.1");
        assertThat(keyword.at("/hits/0/snippet").asString()).contains("**Osmosis**");
        assertThat(keyword.at("/hits/0/breadcrumb").asString()).isEqualTo("Chapter 1 The Cell › Osmosis");
        assertThat(keyword.at("/figures/0/caption").asString()).contains("Osmosis");

        // Scope to chapter 2: the osmosis passage is outside it.
        JsonNode scoped = getJson("/api/documents/{d}/search?q=osmosis&nodeId=" + node(2), documentId);
        assertThat(scoped.at("/hits").toString()).doesNotContain("\"1.1\"");

        // Embedding outage degrades to keyword-only search instead of failing.
        doThrow(new KnowledgeAiException("down")).when(knowledgeAi)
                .embed(eq(KnowledgeAi.EmbedTask.RETRIEVAL_QUERY), any());
        assertThat(getJson("/api/documents/{d}/search?q=mitochondria", documentId).at("/hits/0/label").asString())
                .isEqualTo("Chapter 2");
    }

    @Test
    void switchingTheEmbeddingModelReembedsInTheBackground() throws Exception {
        when(knowledgeAi.embed(any(), any())).thenAnswer(inv -> inv.getArgument(1) == null ? null
                : new KnowledgeAi.Embeddings("fake:embed-v2", FakeAi.embed(inv.getArgument(1))));
        assertThat(store.hasStaleEmbeddings(documentId, "fake:embed-v2")).isTrue();

        // Old vectors are never compared with the new model's query; keyword search still answers.
        assertThat(getJson("/api/documents/{d}/search?q=osmosis", documentId).at("/hits/0/label").asString())
                .isEqualTo("1.1");
        for (int i = 0; i < 100 && store.hasStaleEmbeddings(documentId, "fake:embed-v2"); i++) {
            Thread.sleep(50);
        }
        assertThat(store.hasStaleEmbeddings(documentId, "fake:embed-v2")).isFalse();
        assertThat(store.chunksWithoutEmbedding(documentId, 10)).isEmpty();
    }

    @Test
    void textOnlyFiguresBecomeSearchableAnnotatedText() throws Exception {
        KnowledgeStore.FigureRow figure = store.figuresOfDocument(documentId).getFirst();
        String note = KnowledgeIndexer.annotation(new KnowledgeAi.FigureDescription("k", "SCREENSHOT",
                KnowledgeAi.FigureRole.TEXTUAL, "Terminal output of minikube start.", "$ minikube start\nDone!"));
        store.appendToFigurePassage(figure, note);

        assertThat(note).startsWith("*Screenshot:* Terminal output").contains("```\n$ minikube start\nDone!\n```");
        assertThat(store.chunksWithoutEmbedding(documentId, 10)).singleElement()
                .satisfies(c -> assertThat(c.content()).endsWith(note));
        assertThat(getJson("/api/documents/{d}/search?q=minikube", documentId).at("/hits/0/snippet").asString())
                .contains("minikube");
    }

    @Test
    void termIndexMapsTermsToSections() throws Exception {
        JsonNode terms = getJson("/api/documents/{d}/terms", documentId).get("terms");
        assertThat(terms.toString()).contains("\"term\":\"osmosis\"").contains("\"term\":\"membrane\"");
    }

    @Test
    void notesAreGeneratedOnceWithSourcesAndCached() throws Exception {
        when(ai.notes(any())).thenReturn(FakeAi.notes("## Osmosis notes"));
        String sectionId = node(1);
        JsonNode first = getJson("/api/documents/{d}/nodes/{n}/notes", documentId, sectionId);
        assertThat(first.get("scope").asString()).isEqualTo("section");
        assertThat(first.get("markdown").asString()).startsWith("## Osmosis notes");
        assertThat(first.at("/sources/0/citation").asString()).isEqualTo("§1.1");
        getJson("/api/documents/{d}/nodes/{n}/notes", documentId, sectionId);
        verify(ai, times(1)).notes(any());
    }

    @Test
    void askAnswersWithCitationsAndTheDocumentsFigures() throws Exception {
        when(ai.answer(any())).thenAnswer(inv -> {
            StudyAi.AnswerSpec spec = inv.getArgument(0);
            assertThat(spec.style()).isEqualTo("answer");
            assertThat(spec.sections()).isNotEmpty();
            return FakeAi.answer(spec.figures().isEmpty() ? "none" : spec.figures().getFirst().id());
        });
        JsonNode answer = json.readTree(mvc.perform(post("/api/documents/{d}/ask", documentId)
                        .header("Authorization", user.bearer()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Show me a diagram of osmosis\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
        assertThat(answer.get("grounded").asBoolean()).isTrue();
        assertThat(answer.at("/sources/0/label").asString()).isEqualTo("1.1");
        assertThat(answer.get("figures").size()).isEqualTo(1);
    }

    @Test
    void otherUsersCannotReadTheKnowledgeStore() throws Exception {
        Registered other = TestUsers.register(mvc, json);
        mvc.perform(get("/api/documents/{d}/tree", documentId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/documents/{d}/search?q=osmosis", documentId).header("Authorization", other.bearer()))
                .andExpect(status().isNotFound());
        mvc.perform(post("/api/documents/{d}/ask", documentId).header("Authorization", other.bearer())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"question\":\"x\"}"))
                .andExpect(status().isNotFound());
    }

    private String node(int index) {
        return tree.get("nodes").get(index).get("id").asString();
    }

    private JsonNode getJson(String path, Object... vars) throws Exception {
        return json.readTree(mvc.perform(get(path, vars).header("Authorization", user.bearer()))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }
}
