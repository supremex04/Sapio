package com.ved.knowledge.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.knowledge.ai.KnowledgeAiException;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.knowledge.store.KnowledgeStore.Ranked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Hybrid retrieval: keyword (inverted index, exact terms/formulas/acronyms) + vector (meaning), fused with
 * Reciprocal Rank Fusion. If the embedding service is down, keyword results still work.
 */
@Component
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);
    static final int CANDIDATES = 30;
    static final int RRF_K = 60;

    private final KnowledgeStore store;
    private final KnowledgeAi ai;

    HybridRetriever(KnowledgeStore store, KnowledgeAi ai) {
        this.store = store;
        this.ai = ai;
    }

    /** A fused hit; {@code snippet} prefers the keyword highlight. */
    public record Hit(UUID id, UUID nodeId, double score, String snippet) {
    }

    public record Results(List<Hit> chunks, List<Hit> figures) {
    }

    /** @param scopePaths ltree paths of subtrees to search in; empty = whole document */
    public Results search(UUID documentId, String query, List<String> scopePaths, int limit, int figureLimit) {
        float[] vector = embedQuery(query);
        List<Ranked> keyword = store.keywordChunks(documentId, query, scopePaths, CANDIDATES);
        List<Ranked> dense = vector == null ? List.of() : store.vectorChunks(documentId, vector, scopePaths, CANDIDATES);
        List<Hit> chunks = fuse(List.of(keyword, dense), limit);

        List<Hit> figures = List.of();
        if (figureLimit > 0) {
            List<Ranked> fk = store.keywordFigures(documentId, query, CANDIDATES);
            List<Ranked> fv = vector == null ? List.of() : store.vectorFigures(documentId, vector, CANDIDATES);
            figures = fuse(List.of(fk, fv), figureLimit);
        }
        return new Results(chunks, figures);
    }

    private float[] embedQuery(String query) {
        try {
            return ai.embed(KnowledgeAi.EmbedTask.RETRIEVAL_QUERY, List.of(KnowledgeAi.EmbedInput.text(query))).getFirst();
        } catch (KnowledgeAiException e) {
            log.warn("Query embedding unavailable, keyword-only search: {}", e.getMessage());
            return null;
        }
    }

    /** RRF: score = Σ 1 / (k + rank). Robust to the two lists' incomparable score scales. */
    static List<Hit> fuse(List<List<Ranked>> lists, int limit) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, Ranked> best = new LinkedHashMap<>();
        for (List<Ranked> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                Ranked r = list.get(rank);
                scores.merge(r.id(), 1.0 / (RRF_K + rank + 1), Double::sum);
                best.merge(r.id(), r, (a, b) -> a.snippet() != null && a.snippet().contains("**") ? a : b);
            }
        }
        List<Hit> hits = new ArrayList<>();
        best.forEach((id, r) -> hits.add(new Hit(id, r.nodeId(), scores.get(id), r.snippet())));
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() <= limit ? hits : hits.subList(0, limit);
    }
}
