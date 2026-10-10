package com.ved.knowledge;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.ved.common.error.ApplicationException;
import com.ved.document.DocumentIndexer;
import com.ved.document.DocumentRepository;
import com.ved.document.extraction.ExtractionResult;
import com.ved.document.storage.DocumentStorage;
import com.ved.knowledge.ai.KnowledgeAi;
import com.ved.knowledge.ai.KnowledgeAi.EmbedInput;
import com.ved.knowledge.ai.KnowledgeAiException;
import com.ved.knowledge.retrieval.ContextAssembler;
import com.ved.knowledge.store.KnowledgeStore;
import com.ved.knowledge.store.KnowledgeStore.ChunkRow;
import com.ved.knowledge.store.KnowledgeStore.FigureRow;
import com.ved.knowledge.store.KnowledgeStore.NodeRow;
import com.ved.knowledge.store.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Pipeline stages owned by the knowledge store (implements the document module's {@link DocumentIndexer}).
 * Indexing is resumable: each step only processes rows that are still missing their data, and commits
 * per batch, so a retry after a failure continues where it stopped.
 */
@Component
class KnowledgeIndexer implements DocumentIndexer {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeIndexer.class);

    private final KnowledgeStore store;
    private final DocumentStorage storage;
    private final DocumentRepository documents;
    private final KnowledgeAi ai;
    private final TransactionTemplate tx;
    private final KnowledgeProperties.Indexing config;
    private final Set<UUID> refreshing = ConcurrentHashMap.newKeySet();

    KnowledgeIndexer(KnowledgeStore store, DocumentStorage storage, DocumentRepository documents, KnowledgeAi ai,
                     TransactionTemplate tx, KnowledgeProperties properties) {
        this.config = properties.indexing();
        this.store = store;
        this.storage = storage;
        this.documents = documents;
        this.ai = ai;
        this.tx = tx;
    }

    @Override
    @Transactional(propagation = Propagation.MANDATORY)
    public void ingest(UUID documentId, String language, ExtractionResult.Tree tree) {
        List<String> oldFiles = store.deleteTree(documentId);
        Map<String, String> files = new HashMap<>();
        for (ExtractionResult.Figure f : tree.figures()) {
            files.put(f.key(), storage.store(new ByteArrayInputStream(f.image()), extension(f.mime())));
        }
        // Keep storage consistent with the database whatever the outcome of the transaction.
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                (status == STATUS_COMMITTED ? oldFiles : files.values()).forEach(KnowledgeIndexer.this::deleteQuietly);
            }
        });
        store.insertTree(documentId, SearchConfig.forLanguage(language), tree, files);
        log.info("Document {}: stored {} nodes, {} chunks, {} figures", documentId, tree.nodes().size(),
                tree.chunks().size(), tree.figures().size());
    }

    @Override
    public void index(UUID documentId) throws IndexingException {
        String language = documents.findById(documentId).map(d -> d.getLanguage()).filter(l -> !"und".equals(l))
                .orElse("en");
        try {
            describeFigures(documentId, language);
            summarize(documentId, language);
            embedChunks(documentId);
            embedFigures(documentId);
        } catch (KnowledgeAiException e) {
            if (e.isRejected()) {
                throw new IndexingException("Indexing was rejected: " + e.getMessage(), e);
            }
            throw new IndexingException("The AI service is busy (rate limit)", e, true);
        } catch (ApplicationException e) {
            throw new IndexingException("Indexing failed: " + e.getMessage(), e);
        }
    }

    // ------------------------------------------------------------------------------- steps

    /** Vision descriptions; decorative figures (logos, ornaments) are removed. */
    private void describeFigures(UUID documentId, String language) {
        List<FigureRow> pending = store.figuresWithoutDescription(documentId);
        if (pending.isEmpty()) {
            return;
        }
        Map<UUID, NodeRow> nodes = nodeMap(documentId);
        for (int i = 0; i < pending.size(); i += config.figureBatch()) {
            List<FigureRow> batch = pending.subList(i, Math.min(i + config.figureBatch(), pending.size()));
            List<KnowledgeAi.FigureInput> inputs = batch.stream()
                    .map(f -> new KnowledgeAi.FigureInput(f.id().toString(), read(f.storageKey()), f.mime(), f.caption(),
                            breadcrumb(nodes, f.nodeId())))
                    .toList();
            Map<String, KnowledgeAi.FigureDescription> byKey = ai.describeFigures(language, inputs).stream()
                    .collect(Collectors.toMap(KnowledgeAi.FigureDescription::key, Function.identity(), (a, b) -> a));
            tx.executeWithoutResult(status -> batch.forEach(f -> {
                KnowledgeAi.FigureDescription d = byKey.get(f.id().toString());
                if (d == null) {
                    return; // retried on the next run
                }
                if (!d.educational()) {
                    store.deleteFigure(f.id());
                    afterCommit(() -> deleteQuietly(f.storageKey()));
                } else {
                    store.updateFigureDescription(f.id(), d.description(), d.kind());
                }
            }));
        }
    }

    /** Bottom-up: deepest nodes first, so parents can be summarised from their children's summaries. */
    private void summarize(UUID documentId, String language) {
        List<NodeRow> pending = store.nodesWithoutSummary(documentId);
        if (pending.isEmpty()) {
            return;
        }
        Map<Integer, List<NodeRow>> byLevel = pending.stream()
                .collect(Collectors.groupingBy(NodeRow::level, LinkedHashMap::new, Collectors.toList()));
        for (List<NodeRow> level : byLevel.values()) {
            Map<UUID, NodeRow> all = nodeMap(documentId); // refreshed: includes summaries of the level below
            Map<UUID, List<NodeRow>> children = all.values().stream().filter(n -> n.parentId() != null)
                    .collect(Collectors.groupingBy(NodeRow::parentId));
            for (int i = 0; i < level.size(); i += config.summaryBatch()) {
                List<NodeRow> batch = level.subList(i, Math.min(i + config.summaryBatch(), level.size()));
                List<KnowledgeAi.SummaryInput> inputs = batch.stream().map(n -> new KnowledgeAi.SummaryInput(
                        n.id().toString(), breadcrumb(all, n.id()), ownText(n.id()),
                        children.getOrDefault(n.id(), List.of()).stream()
                                .filter(c -> c.summary() != null)
                                .map(c -> c.label() + " " + c.title() + ": " + c.summary())
                                .toList())).toList();
                List<KnowledgeAi.NodeSummary> summaries = ai.summarize(language, inputs);
                tx.executeWithoutResult(status -> summaries.forEach(s -> {
                    UUID nodeId = UUID.fromString(s.key());
                    store.updateSummary(nodeId, s.summary(), s.keyTerms());
                    store.insertTerms(documentId, nodeId, s.keyTerms());
                }));
            }
        }
    }

    /** Leaf chunks are embedded with their breadcrumb, so "osmosis in plants" matches the right section. */
    private void embedChunks(UUID documentId) {
        Map<UUID, NodeRow> nodes = nodeMap(documentId);
        List<ChunkRow> pending;
        while (!(pending = store.chunksWithoutEmbedding(documentId, config.chunkEmbedBatch())).isEmpty()) {
            List<ChunkRow> batch = pending;
            KnowledgeAi.Embeddings e = ai.embed(KnowledgeAi.EmbedTask.RETRIEVAL_DOCUMENT, batch.stream()
                    .map(c -> EmbedInput.text(breadcrumb(nodes, c.nodeId()) + "\n" + c.content()))
                    .toList());
            tx.executeWithoutResult(status -> store.updateChunkEmbeddings(e.model(), byId(batch, ChunkRow::id, e)));
        }
    }

    /** Figures are embedded multimodally: the image itself plus its caption and description. */
    private void embedFigures(UUID documentId) {
        List<FigureRow> pending;
        while (!(pending = store.figuresWithoutEmbedding(documentId, config.figureEmbedBatch())).isEmpty()) {
            List<FigureRow> batch = pending;
            KnowledgeAi.Embeddings e = ai.embed(KnowledgeAi.EmbedTask.RETRIEVAL_DOCUMENT, batch.stream()
                    .map(f -> new EmbedInput((f.caption() + "\n" + f.description()).strip(), read(f.storageKey()), f.mime()))
                    .toList());
            tx.executeWithoutResult(status -> store.updateFigureEmbeddings(e.model(), byId(batch, FigureRow::id, e)));
        }
    }

    /**
     * The embedding model was switched: re-embed the document's stale vectors in the background. Runs on the
     * document worker pool, so it shares the cap on concurrent inference calls.
     */
    @Async("documentProcessingExecutor")
    @EventListener
    public void onStaleEmbeddings(StaleEmbeddingsDetected event) {
        UUID documentId = event.documentId();
        if (!refreshing.add(documentId)) {
            return; // already in progress
        }
        try {
            int cleared = tx.execute(status -> store.clearStaleEmbeddings(documentId, event.model()));
            log.info("Document {}: re-embedding {} vectors with {}", documentId, cleared, event.model());
            embedChunks(documentId);
            embedFigures(documentId);
        } catch (KnowledgeAiException e) {
            log.warn("Document {}: re-embedding paused ({}); it resumes on the next search", documentId,
                    e.getMessage());
        } finally {
            refreshing.remove(documentId);
        }
    }

    // ------------------------------------------------------------------------------- helpers

    private static <T> Map<UUID, float[]> byId(List<T> batch, Function<T, UUID> id, KnowledgeAi.Embeddings e) {
        Map<UUID, float[]> updates = new LinkedHashMap<>();
        for (int i = 0; i < batch.size(); i++) {
            updates.put(id.apply(batch.get(i)), e.vectors().get(i));
        }
        return updates;
    }

    private Map<UUID, NodeRow> nodeMap(UUID documentId) {
        return store.nodes(documentId).stream().collect(Collectors.toMap(NodeRow::id, Function.identity()));
    }

    private String ownText(UUID nodeId) {
        StringBuilder sb = new StringBuilder();
        for (ChunkRow c : store.chunksOfNode(nodeId)) {
            if (sb.length() >= config.summaryTextChars()) {
                break;
            }
            sb.append(c.content()).append("\n\n");
        }
        return sb.length() > config.summaryTextChars() ? sb.substring(0, config.summaryTextChars()) : sb.toString();
    }

    private static String breadcrumb(Map<UUID, NodeRow> nodes, UUID nodeId) {
        NodeRow node = nodes.get(nodeId);
        return node == null ? "" : ContextAssembler.breadcrumb(nodes, node);
    }

    private byte[] read(String storageKey) {
        try (InputStream in = storage.load(storageKey).getInputStream()) {
            return in.readAllBytes();
        } catch (IOException e) {
            throw new IllegalStateException("Figure file missing: " + storageKey, e);
        }
    }

    private void deleteQuietly(String key) {
        try {
            storage.delete(key);
        } catch (RuntimeException e) {
            log.warn("Could not delete figure file {}", key, e);
        }
    }

    private static void afterCommit(Runnable action) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                action.run();
            }
        });
    }

    private static String extension(String mime) {
        return "image/jpeg".equals(mime) ? "jpg" : "png";
    }
}
