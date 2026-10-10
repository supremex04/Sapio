package com.ved.document.processing;

import java.time.Clock;
import java.util.UUID;
import java.util.function.Consumer;

import com.ved.document.Document;
import com.ved.document.DocumentAnalyzer;
import com.ved.document.DocumentAnalyzer.AnalysisException;
import com.ved.document.DocumentIndexer;
import com.ved.document.DocumentIndexer.IndexingException;
import com.ved.document.DocumentRepository;
import com.ved.document.DocumentSegment;
import com.ved.document.DocumentSegmentRepository;
import com.ved.document.DocumentStatus;
import com.ved.document.extraction.ExtractionException;
import com.ved.document.extraction.ExtractionGateway;
import com.ved.document.extraction.ExtractionRequest;
import com.ved.document.extraction.ExtractionResult;
import com.ved.document.storage.DocumentStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
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

    private final DocumentRepository documents;
    private final DocumentSegmentRepository segments;
    private final DocumentStorage storage;
    private final ExtractionGateway extraction;
    private final DocumentIndexer indexer;
    private final DocumentAnalyzer analyzer;
    private final TransactionTemplate tx;
    private final Clock clock;

    DocumentProcessor(DocumentRepository documents, DocumentSegmentRepository segments, DocumentStorage storage,
                      ExtractionGateway extraction, DocumentIndexer indexer, DocumentAnalyzer analyzer,
                      TransactionTemplate tx, Clock clock) {
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

    @Async(ProcessingConfig.EXECUTOR)
    public void process(UUID documentId) {
        Claim claim = tx.execute(status -> claim(documentId));
        if (claim == null) {
            return; // deleted, or already picked up
        }
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
        } catch (ExtractionException | IndexingException | AnalysisException e) {
            log.info("Document {} failed: {}", documentId, e.getMessage());
            update(documentId, d -> d.markFailed(e.getMessage(), clock.instant()));
            settled = true;
        } finally {
            // Anything unexpected (including Errors) must not leave the document stuck mid-pipeline.
            if (!settled) {
                log.error("Unexpected error while processing document {}", documentId);
                update(documentId, d -> d.markFailed("Unexpected processing error", clock.instant()));
            }
        }
    }

    private Claim claim(UUID documentId) {
        Document doc = documents.findById(documentId).orElse(null);
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
