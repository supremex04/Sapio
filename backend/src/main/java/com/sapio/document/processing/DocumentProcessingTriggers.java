package com.sapio.document.processing;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import com.sapio.document.Document;
import com.sapio.document.DocumentProperties;
import com.sapio.document.DocumentRepository;
import com.sapio.document.DocumentStatus;
import com.sapio.document.DocumentQueuedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Decides <em>when</em> documents get processed: right after upload, when an automatic retry is due, and when
 * a job was abandoned (its instance stopped). Works with any number of backend instances: claiming is atomic
 * and every in-flight document is covered by a heartbeat lease.
 */
@Component
class DocumentProcessingTriggers {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingTriggers.class);

    private final DocumentProcessor processor;
    private final DocumentRepository documents;
    private final TransactionTemplate tx;
    private final Clock clock;
    private final Duration lease;

    private static final Set<DocumentStatus> IN_FLIGHT = EnumSet.of(DocumentStatus.PENDING,
            DocumentStatus.EXTRACTING, DocumentStatus.INDEXING, DocumentStatus.ANALYZING);

    DocumentProcessingTriggers(DocumentProcessor processor, DocumentRepository documents, TransactionTemplate tx,
                               Clock clock, DocumentProperties properties) {
        this.lease = properties.processing().lease();
        this.processor = processor;
        this.documents = documents;
        this.tx = tx;
        this.clock = clock;
    }

    @TransactionalEventListener
    void onQueued(DocumentQueuedEvent event) {
        submit(event.documentId());
    }

    /**
     * Requeues documents nobody is working on: in flight on an instance that stopped (its heartbeat expired),
     * or queued but never started (a full queue). Safe on every instance at once: claiming is atomic.
     */
    @EventListener(ApplicationReadyEvent.class)
    @Scheduled(fixedDelayString = "${sapio.documents.processing.retry-poll:30s}",
            initialDelayString = "${sapio.documents.processing.retry-poll:30s}")
    void resumeAbandoned() {
        Instant now = clock.instant();
        // Requeue in its own committed transaction first, so workers see the PENDING state.
        List<UUID> abandoned = tx.execute(status -> {
            List<Document> docs = documents.findAbandoned(IN_FLIGHT, now.minus(lease));
            docs.forEach(d -> d.requeue(now));
            return docs.stream().map(Document::getId).toList();
        });
        if (abandoned != null && !abandoned.isEmpty()) {
            log.info("Resuming {} abandoned document(s)", abandoned.size());
            abandoned.forEach(this::submit);
        }
    }

    /** Keeps this instance's leases alive while it works on documents. */
    @Scheduled(fixedDelayString = "${sapio.documents.processing.heartbeat-interval:30s}")
    void heartbeat() {
        Set<UUID> ids = processor.inFlight();
        if (!ids.isEmpty()) {
            Instant now = clock.instant();
            tx.executeWithoutResult(status -> documents.heartbeat(ids, now));
        }
    }

    /** Resumes documents whose automatic retry is due (transient AI failures). */
    @Scheduled(fixedDelayString = "${sapio.documents.processing.retry-poll:30s}")
    void resumeDueRetries() {
        Instant now = clock.instant();
        List<UUID> due = tx.execute(status -> {
            List<Document> docs = documents.findByStatusAndRetryAtLessThanEqual(DocumentStatus.FAILED, now);
            docs.forEach(d -> d.resume(now));
            return docs.stream().map(Document::getId).toList();
        });
        if (due != null && !due.isEmpty()) {
            log.info("Automatically retrying {} document(s)", due.size());
            due.forEach(this::submit);
        }
    }

    private void submit(UUID documentId) {
        try {
            processor.submit(documentId);
        } catch (TaskRejectedException e) {
            // Stays PENDING; picked up again once its lease expires (resumeAbandoned).
            log.warn("Processing queue full, document {} deferred", documentId);
        }
    }
}
