package com.ved.document.extraction;

/** Extraction failed. The message is safe to show to the document owner. */
public class ExtractionException extends Exception {

    public ExtractionException(String message) {
        super(message);
    }

    public ExtractionException(String message, Throwable cause) {
        super(message, cause);
    }
}
