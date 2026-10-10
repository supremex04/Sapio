package com.ved.document;

import java.util.UUID;

import com.ved.document.extraction.ExtractionResult;

/**
 * Knowledge-store stages of the pipeline, implemented outside this module (the knowledge module) so
 * that {@code document} does not depend on it. Both operations must be idempotent.
 */
public interface DocumentIndexer {

    /** Stores the extracted tree (nodes, chunks, figure files), replacing any previous one. Fast; no AI calls. */
    void ingest(UUID documentId, String language, ExtractionResult.Tree tree);

    /** Fills whatever is still missing: figure descriptions, summaries, term index, embeddings. */
    void index(UUID documentId) throws IndexingException;

    /** The message is safe to show to the document owner. */
    class IndexingException extends Exception {
        public IndexingException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
