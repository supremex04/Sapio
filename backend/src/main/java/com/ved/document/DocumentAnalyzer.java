package com.ved.document;

import java.util.UUID;

/**
 * Last pipeline stage: turns the indexed document into something learnable (the study module's learning
 * path). Implemented outside this module so that {@code document} does not depend on {@code study}.
 * Implementations must be idempotent: a retry may call it again for the same document.
 */
public interface DocumentAnalyzer {

    Result analyze(Input input) throws AnalysisException;

    record Input(UUID documentId, UUID ownerId, String filename) {
    }

    /** @param title human-friendly document title derived from the content */
    record Result(String title) {
    }

    /** The message is safe to show to the document owner. */
    class AnalysisException extends Exception {
        public AnalysisException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
