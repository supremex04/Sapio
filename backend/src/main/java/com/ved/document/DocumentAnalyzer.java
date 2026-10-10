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
        private final boolean transientFailure;

        public AnalysisException(String message, Throwable cause) {
            this(message, cause, false);
        }

        /** @param transientFailure the AI provider was busy/unavailable: worth retrying automatically */
        public AnalysisException(String message, Throwable cause, boolean transientFailure) {
            super(message, cause);
            this.transientFailure = transientFailure;
        }

        public boolean isTransient() {
            return transientFailure;
        }
    }
}
