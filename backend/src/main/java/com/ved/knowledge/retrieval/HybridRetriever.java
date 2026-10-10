package com.ved.knowledge.retrieval;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.ved.knowledge.KnowledgeProperties;
import com.ved.knowledge.StaleEmbeddingsDetected;
import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.knowledge.ai.KnowledgeAiException;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.knowledge.store.KnowledgeStore.Ranked;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Hybrid retrieval: keyword (inverted index, exact terms/formulas/acronyms) + vector (meaning), fused with
 * Reciprocal Rank Fusion. If the embedding service is down, keyword results still work.
 */
@Component
public class HybridRetriever {

    private static final Logger log = LoggerFactory.getLogger(HybridRetriever.class);

    private final KnowledgeStore store;
    private final KnowledgeAi ai;
    private final KnowledgeProperties.Retrieval config;
    private final ApplicationEventPublisher events;

    HybridRetriever(KnowledgeStore store, KnowledgeAi ai, KnowledgeProperties properties,
                    ApplicationEventPublisher events) {
        this.store = store;
        this.ai = ai;
        this.config = properties.retrieval();
        this.events = events;
    }

    /** A fused hit; {@code snippet} prefers the keyword highlight. */
    public record Hit(UUID id, UUID nodeId, double score, String snippet) {
    }

    public record Results(List<Hit> chunks, List<Hit> figures) {
    }

    /** @param scopePaths ltree paths of subtrees to search in; empty = whole document */
    public Results search(UUID documentId, String query, List<String> scopePaths, int limit, int figureLimit) {
        KnowledgeAi.Embeddings q = embedQuery(documentId, query);
        int n = config.candidates();
        List<Ranked> keyword = store.keywordChunks(documentId, query, scopePaths, n);
        List<Ranked> dense = q == null ? List.of()
                : store.vectorChunks(documentId, q.model(), q.vectors().getFirst(), scopePaths, n);
        List<Double> weights = List.of(config.keywordWeight(), config.vectorWeight());
        List<Hit> chunks = fuse(List.of(keyword, dense), weights, limit, config.rrfK());

        List<Hit> figures = List.of();
        if (figureLimit > 0) {
            List<Ranked> fk = store.keywordFigures(documentId, query, n);
            List<Ranked> fv = q == null ? List.of() : store.vectorFigures(documentId, q.model(), q.vectors().getFirst(),
                    config.figureMinSimilarity(), n);
            figures = fuse(List.of(fk, fv), weights, figureLimit, config.rrfK());
        }
        return new Results(chunks, figures);
    }

    private KnowledgeAi.Embeddings embedQuery(UUID documentId, String query) {
        KnowledgeAi.Embeddings q;
        try {
            q = ai.embed(KnowledgeAi.EmbedTask.RETRIEVAL_QUERY, List.of(KnowledgeAi.EmbedInput.text(query)));
        } catch (KnowledgeAiException e) {
            log.warn("Query embedding unavailable, keyword-only search: {}", e.getMessage());
            return null;
        }
        if (store.hasStaleEmbeddings(documentId, q.model())) {
            events.publishEvent(new StaleEmbeddingsDetected(documentId, q.model()));
        }
        return q;
    }

    /**
     * Weighted RRF: score = Σ weight / (k + rank). Uses ranks only, so the lists' incomparable score scales don't
     * matter; the weights say how much each ranker is trusted.
     */
    static List<Hit> fuse(List<List<Ranked>> lists, List<Double> weights, int limit, int k) {
        Map<UUID, Double> scores = new HashMap<>();
        Map<UUID, Ranked> best = new LinkedHashMap<>();
        for (int l = 0; l < lists.size(); l++) {
            List<Ranked> list = lists.get(l);
            double weight = weights.get(l);
            for (int rank = 0; rank < list.size(); rank++) {
                Ranked r = list.get(rank);
                scores.merge(r.id(), weight / (k + rank + 1), Double::sum);
                best.merge(r.id(), r, (a, b) -> a.snippet() != null && a.snippet().contains("**") ? a : b);
            }
        }
        List<Hit> hits = new ArrayList<>();
        best.forEach((id, r) -> hits.add(new Hit(id, r.nodeId(), scores.get(id), r.snippet())));
        hits.sort(Comparator.comparingDouble(Hit::score).reversed());
        return hits.size() <= limit ? hits : hits.subList(0, limit);
    }
}
