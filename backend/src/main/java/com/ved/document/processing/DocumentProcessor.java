package com.ved.document.processing;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

import com.ved.document.Document;
import com.ved.document.DocumentRepository;
import com.ved.document.DocumentStatus;
import com.ved.document.ocr.OcrException;
import com.ved.document.ocr.OcrGateway;
import com.ved.document.ocr.OcrRequest;
import com.ved.document.ocr.OcrResult;
import com.ved.document.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Runs one document through OCR. State changes happen in short transactions; the (slow) OCR call runs
 * outside any transaction so no DB connection is held while waiting on the model.
 */
@Component
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final DocumentRepository documents;
    private final DocumentStorage storage;
    private final OcrGateway ocr;
    private final TransactionTemplate tx;
    private final Clock clock;

    DocumentProcessor(DocumentRepository documents, DocumentStorage storage, OcrGateway ocr,
                      TransactionTemplate tx, Clock clock) {
        this.documents = documents;
        this.storage = storage;
        this.ocr = ocr;
        this.tx = tx;
        this.clock = clock;
    }

    @Async(ProcessingConfig.EXECUTOR)
    public void process(UUID documentId) {
        OcrRequest request = tx.execute(status -> claim(documentId));
        if (request == null) {
            return; // deleted, or already picked up
        }
        boolean settled = false;
        try {
            OcrResult result = ocr.extract(request);
            update(documentId, d -> d.markCompleted(result.markdown(), result.engine(), result.pageCount(),
                    clock.instant()));
            settled = true;
            log.info("Document {} converted by {}", documentId, result.engine());
        } catch (OcrException e) {
            log.info("Document {} failed: {}", documentId, e.getMessage());
            update(documentId, d -> d.markFailed(e.getMessage(), clock.instant()));
            settled = true;
        } finally {
            // Anything unexpected (including Errors) must not leave the document stuck in PROCESSING.
            if (!settled) {
                log.error("Unexpected error while processing document {}", documentId);
                update(documentId, d -> d.markFailed("Unexpected processing error", clock.instant()));
            }
        }
    }

    private OcrRequest claim(UUID documentId) {
        Document doc = documents.findById(documentId).orElse(null);
        if (doc == null || doc.getStatus() != DocumentStatus.PENDING) {
            return null;
        }
        doc.markProcessing(clock.instant());
        return new OcrRequest(storage.load(doc.getStorageKey()), doc.getOriginalFilename(), doc.getContentType());
    }

    private void update(UUID documentId, Consumer<Document> change) {
        tx.executeWithoutResult(status -> documents.findById(documentId).ifPresent(change));
    }
}
