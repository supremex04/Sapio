package com.sapio.knowledge;

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

import com.sapio.common.error.ApplicationException;
import com.sapio.common.error.ErrorCode;
import com.sapio.document.DocumentIndexer;
import com.sapio.document.DocumentRepository;
import com.sapio.document.DocumentStatus;
import com.sapio.document.extraction.ExtractionResult;
import com.sapio.document.storage.DocumentStorage;
import com.sapio.knowledge.ai.KnowledgeAi;
import com.sapio.knowledge.ai.KnowledgeAi.EmbedInput;
import com.sapio.knowledge.ai.KnowledgeAiException;
import com.sapio.knowledge.retrieval.ContextAssembler;
import com.sapio.knowledge.store.KnowledgeStore;
import com.sapio.knowledge.store.KnowledgeStore.ChunkRow;
import com.sapio.knowledge.store.KnowledgeStore.FigureRow;
import com.sapio.knowledge.store.KnowledgeStore.NodeRow;
import com.sapio.knowledge.store.SearchConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.event.TransactionalEventListener;
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
    private final Set<UUID> again = ConcurrentHashMap.newKeySet();

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
            // Anything but a rejected input is fixed by waiting (busy, quota) or by an operator: resume later.
            throw new IndexingException(e.isInputRejected() ? "Indexing was rejected: " + e.getMessage()
                    : e.getMessage(), e, !e.isInputRejected(), e.retryAfter().orElse(null));
        } catch (ApplicationException e) {
            throw new IndexingException("Indexing failed: " + e.getMessage(), e, false, null);
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
                if (d.role() == KnowledgeAi.FigureRole.TEXTUAL) {
                    // Text-only image (terminal output, code): its words matter, not its pixels.
                    store.appendToFigurePassage(f, annotation(d));
                    store.deleteFigure(f.id());
                    afterCommit(() -> deleteQuietly(f.storageKey()));
                } else if (d.role() != KnowledgeAi.FigureRole.VISUAL) {
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
     * The embedding model was switched, or some text has no vector yet: re-embed in the background. Runs on the
     * document worker pool, so it shares the cap on concurrent inference calls.
     */
    @Async("documentProcessingExecutor")
    @EventListener
    public void onStaleEmbeddings(StaleEmbeddingsDetected event) {
        refresh(event.documentId(), event.model());
    }

    /** A learner edited a section: embed its new chunks once the edit is committed (no summaries: see below). */
    @Async("documentProcessingExecutor")
    @TransactionalEventListener
    public void onSectionEdited(SectionEdited event) {
        refresh(event.documentId(), null);
    }

    /**
     * Brings a ready document's vectors up to date: clears those of another model (if {@code staleModel} is set),
     * then embeds what has none. One run per document at a time; a request that arrives during a run makes it go
     * round once more, so a quick second save is never left unembedded.
     */
    private void refresh(UUID documentId, String staleModel) {
        DocumentStatus status = documents.findById(documentId).map(d -> d.getStatus()).orElse(null);
        if (status != DocumentStatus.READY && status != DocumentStatus.ANALYZING) {
            return; // still in the pipeline, whose indexing step does all of this
        }
        if (!refreshing.add(documentId)) {
            again.add(documentId);
            return;
        }
        try {
            do {
                again.remove(documentId);
                if (staleModel != null) {
                    int cleared = tx.execute(s -> store.clearStaleEmbeddings(documentId, staleModel));
                    log.info("Document {}: re-embedding {} vectors with {}", documentId, cleared, staleModel);
                }
                embedChunks(documentId);
                embedFigures(documentId);
            } while (again.remove(documentId));
        } catch (KnowledgeAiException e) {
            log.warn("Document {}: index refresh paused ({}); it resumes on the next search", documentId,
                    e.getMessage());
        } finally {
            refreshing.remove(documentId);
        }
    }

    /**
     * Rewrites the summaries in a subtree that were written from older text than the sections have now (the
     * learner edited them). Called when the learner asks for something built on them, never on save.
     */
    void refreshSummaries(UUID documentId, String path) {
        List<NodeRow> stale = store.nodesWithStaleSummary(documentId, path);
        if (stale.isEmpty()) {
            return;
        }
        tx.executeWithoutResult(status -> store.clearSummaries(stale.stream().map(NodeRow::id).toList()));
        String language = documents.findById(documentId).map(d -> d.getLanguage()).filter(l -> !"und".equals(l))
                .orElse("en");
        try {
            summarize(documentId, language);
        } catch (KnowledgeAiException e) {
            throw new ApplicationException(e.isInputRejected() ? ErrorCode.AI_INPUT_REJECTED : ErrorCode.AI_UNAVAILABLE,
                    "Sapi could not catch up with your changes right now, please retry");
        }
    }

    // ------------------------------------------------------------------------------- helpers

    /** "Screenshot (terminal output of minikube start): …" followed by the transcript as a code block. */
    static String annotation(KnowledgeAi.FigureDescription d) {
        String label = "SCREENSHOT".equals(d.kind()) ? "Screenshot" : "Image";
        String text = "*" + label + ":* " + d.description().strip();
        String transcript = d.transcript() == null ? "" : d.transcript().strip().replace("```", "` ` `");
        return transcript.isEmpty() ? text : text + "\n\n```\n" + transcript + "\n```";
    }

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
