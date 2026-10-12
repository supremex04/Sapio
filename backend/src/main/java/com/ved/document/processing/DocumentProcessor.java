package com.ved.document.processing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;

import jakarta.annotation.PreDestroy;

import com.ved.common.web.RequestId;
import com.ved.document.Document;
import com.ved.document.DocumentAnalyzer;
import com.ved.document.DocumentIndexer;
import com.ved.document.DocumentProperties;
import com.ved.document.DocumentRepository;
import com.ved.document.DocumentSegment;
import com.ved.document.DocumentSegmentRepository;
import com.ved.document.DocumentStatus;
import com.ved.document.PipelineException;
import com.ved.document.extraction.ExtractionException;
import com.ved.document.extraction.ExtractionGateway;
import com.ved.document.extraction.ExtractionRequest;
import com.ved.document.extraction.ExtractionResult;
import com.ved.document.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.core.task.TaskExecutor;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one document through the pipeline: extraction (skipped when already done) → indexing (resumes
 * whatever is missing) → analysis. State changes happen in short transactions; slow calls (OCR, LLM,
 * embeddings) run outside any transaction.
 */
@Component
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);
    /** Retry hints beyond this are not trusted (a misreported reset must not park a document for days). */
    private static final Duration MAX_RETRY_HINT = Duration.ofHours(24);

    private final DocumentRepository documents;
    private final DocumentSegmentRepository segments;
    private final DocumentStorage storage;
    private final ExtractionGateway extraction;
    private final DocumentIndexer indexer;
    private final DocumentAnalyzer analyzer;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final List<Duration> retryDelays;
    private final TaskExecutor executor;

    DocumentProcessor(DocumentRepository documents, DocumentSegmentRepository segments, DocumentStorage storage,
                      ExtractionGateway extraction, DocumentIndexer indexer, DocumentAnalyzer analyzer,
                      TransactionTemplate tx, Clock clock, DocumentProperties properties,
                      @Qualifier(ProcessingConfig.EXECUTOR) TaskExecutor executor) {
        this.executor = executor;
        // Retry schedule for transient failures; progress is kept, so each retry resumes where it stopped.
        this.retryDelays = properties.processing().autoRetryDelays();
        this.documents = documents;
        this.segments = segments;
        this.storage = storage;
        this.extraction = extraction;
        this.indexer = indexer;
        this.analyzer = analyzer;
        this.tx = tx;
        this.clock = clock;
    }

    private record Claim(UUID ownerId, String filename, String contentType, String storageKey, boolean extract) {
    }

    /** Documents queued or running on this instance; their lease is kept alive by heartbeats. */
    private final Set<UUID> inFlight = ConcurrentHashMap.newKeySet();

    /**
     * Queues the document on this instance's worker pool.
     *
     * @throws TaskRejectedException when the queue is full (the document stays PENDING and is picked up later)
     */
    public void submit(UUID documentId) {
        if (!inFlight.add(documentId)) {
            return; // already queued or running here
        }
        try {
            executor.execute(() -> process(documentId));
        } catch (TaskRejectedException e) {
            inFlight.remove(documentId);
            throw e;
        }
    }

    void process(UUID documentId) {
        boolean ownRequestId = RequestId.current().isEmpty();
        if (ownRequestId) {
            MDC.put(RequestId.MDC_KEY, "doc-" + documentId.toString().substring(0, 8)); // scheduled retries
        }
        try {
            Claim claim = tx.execute(status -> claim(documentId));
            if (claim != null) { // else deleted, or claimed by another instance
                run(documentId, claim);
            }
        } finally {
            inFlight.remove(documentId);
            if (ownRequestId) {
                MDC.remove(RequestId.MDC_KEY);
            }
        }
    }

    Set<UUID> inFlight() {
        return Set.copyOf(inFlight);
    }

    /** On shutdown, expire this instance's leases so another instance (or the next start) resumes them at once. */
    @PreDestroy
    void releaseLeases() {
        Set<UUID> ids = inFlight();
        if (!ids.isEmpty()) {
            tx.executeWithoutResult(status -> documents.heartbeat(ids, Instant.EPOCH));
            log.info("Released {} in-flight document(s) for another instance to resume", ids.size());
        }
    }

    private void run(UUID documentId, Claim claim) {
        boolean settled = false;
        try {
            if (claim.extract()) {
                extract(documentId, claim);
            }
            indexer.index(documentId);
            update(documentId, Document::markIndexed);
            DocumentAnalyzer.Result result = analyzer.analyze(
                    new DocumentAnalyzer.Input(documentId, claim.ownerId(), claim.filename()));
            update(documentId, d -> d.markReady(result.title(), clock.instant()));
            settled = true;
            log.info("Document {} is ready", documentId);
        } catch (PipelineException e) {
            fail(documentId, e);
            settled = true;
        } finally {
            // Anything unexpected (including Errors) must not leave the document stuck mid-pipeline.
            if (!settled) {
                log.error("Unexpected error while processing document {}", documentId);
                update(documentId, d -> d.markFailed("Unexpected processing error", clock.instant()));
            }
        }
    }

    private void fail(UUID documentId, PipelineException failure) {
        Instant now = clock.instant();
        String reason = failure.getMessage();
        update(documentId, d -> {
            if (failure.isTransient() && d.getAutoRetries() < retryDelays.size()) {
                // Wait at least as long as the AI said it needs (a quota reset), so retries aren't wasted.
                Duration delay = retryDelays.get(d.getAutoRetries());
                Duration hint = failure.retryAfter().map(h -> h.compareTo(MAX_RETRY_HINT) > 0 ? MAX_RETRY_HINT : h)
                        .orElse(Duration.ZERO);
                Instant at = now.plus(hint.compareTo(delay) > 0 ? hint : delay);
                log.info("Document {} paused ({}), retrying automatically at {}", documentId, reason, at);
                d.markFailed(reason + ". Progress is saved and it will continue automatically.", now, at);
            } else {
                log.info("Document {} failed: {}", documentId, reason);
                d.markFailed(reason, now);
            }
        });
    }

    private Claim claim(UUID documentId) {
        // Row lock: of several instances (or workers) handed the same document, exactly one sees it PENDING.
        Document doc = documents.lockById(documentId).orElse(null);
        if (doc == null || doc.getStatus() != DocumentStatus.PENDING) {
            return null;
        }
        doc.startProcessing(clock.instant());
        return new Claim(doc.getOwnerId(), doc.getOriginalFilename(), doc.getContentType(), doc.getStorageKey(),
                doc.getStatus() == DocumentStatus.EXTRACTING);
    }

    private void extract(UUID documentId, Claim claim) throws ExtractionException {
        ExtractionResult result = extraction.extract(
                new ExtractionRequest(storage.load(claim.storageKey()), claim.filename(), claim.contentType()));
        if (result.segments().stream().allMatch(s -> s.markdown() == null || s.markdown().isBlank())) {
            throw new ExtractionException("No readable text was found in this document");
        }
        tx.executeWithoutResult(status -> {
            indexer.ingest(documentId, result.language(), result.tree());
            segments.deleteByDocumentId(documentId);
            int position = 0;
            for (ExtractionResult.Segment s : result.segments()) {
                segments.save(new DocumentSegment(documentId, position++, s.pageFrom(), s.pageTo(), s.method(),
                        s.markdown() == null ? "" : s.markdown()));
            }
            documents.findById(documentId).ifPresent(d -> d.markExtracted(result.markdown(), result.engine(),
                    result.pageCount(), result.textPages(), result.ocrPages(), result.language()));
        });
    }

    private void update(UUID documentId, Consumer<Document> change) {
        tx.executeWithoutResult(status -> documents.findById(documentId).ifPresent(change));
    }
}
