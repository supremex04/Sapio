package com.ved.document.extraction;

/**
 * Extraction failed. The message is safe to show to the document owner. {@code transientFailure} = the service
 * was unavailable (worth retrying automatically) rather than the document being unreadable.
 */
public class ExtractionException extends Exception {

    private final boolean transientFailure;

    public ExtractionException(String message) {
        this(message, null, false);
    }

    public ExtractionException(String message, Throwable cause) {
        this(message, cause, false);
    }

    public ExtractionException(String message, Throwable cause, boolean transientFailure) {
        super(message, cause);
        this.transientFailure = transientFailure;
    }

    public boolean isTransient() {
        return transientFailure;
    }
}
