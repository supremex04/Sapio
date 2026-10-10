package com.ved.document.processing;

import java.time.Clock;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import com.ved.document.Document;
import com.ved.document.DocumentRepository;
import com.ved.document.DocumentStatus;
import com.ved.document.DocumentQueuedEvent;
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
 * Decides <em>when</em> documents get processed: right after upload, and on startup for jobs that were
 * interrupted by a restart.
 */
@Component
class DocumentProcessingTriggers {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessingTriggers.class);

    private final DocumentProcessor processor;
    private final DocumentRepository documents;
    private final TransactionTemplate tx;
    private final Clock clock;

    DocumentProcessingTriggers(DocumentProcessor processor, DocumentRepository documents, TransactionTemplate tx,
                               Clock clock) {
        this.processor = processor;
        this.documents = documents;
        this.tx = tx;
        this.clock = clock;
    }

    @TransactionalEventListener
    void onQueued(DocumentQueuedEvent event) {
        submit(event.documentId());
    }

    @EventListener(ApplicationReadyEvent.class)
    void resumeInterrupted() {
        // Requeue in its own committed transaction first, so workers see the PENDING state.
        List<UUID> unfinished = tx.execute(status -> {
            List<Document> docs = documents.findByStatusIn(
                    EnumSet.of(DocumentStatus.PENDING, DocumentStatus.EXTRACTING, DocumentStatus.INDEXING,
                            DocumentStatus.ANALYZING));
            docs.forEach(Document::requeue);
            return docs.stream().map(Document::getId).toList();
        });
        if (unfinished != null && !unfinished.isEmpty()) {
            log.info("Resuming {} unfinished document(s)", unfinished.size());
            unfinished.forEach(this::submit);
        }
    }

    /** Resumes documents whose automatic retry is due (transient AI failures). */
    @Scheduled(fixedDelayString = "${ved.documents.processing.retry-poll:30s}")
    void resumeDueRetries() {
        Instant now = clock.instant();
        List<UUID> due = tx.execute(status -> {
            List<Document> docs = documents.findByStatusAndRetryAtLessThanEqual(DocumentStatus.FAILED, now);
            docs.forEach(Document::resume);
            return docs.stream().map(Document::getId).toList();
        });
        if (due != null && !due.isEmpty()) {
            log.info("Automatically retrying {} document(s)", due.size());
            due.forEach(this::submit);
        }
    }

    private void submit(UUID documentId) {
        try {
            processor.process(documentId);
        } catch (TaskRejectedException e) {
            // Stays PENDING; picked up again on next startup.
            log.warn("Processing queue full, document {} deferred", documentId);
        }
    }
}
