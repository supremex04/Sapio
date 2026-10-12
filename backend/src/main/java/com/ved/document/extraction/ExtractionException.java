package com.ved.document.extraction;

import java.time.Duration;

import com.ved.document.PipelineException;

/** Extraction failed. See {@link PipelineException}. */
public class ExtractionException extends PipelineException {

    /** The document itself cannot be read. */
    public ExtractionException(String message) {
        this(message, null, false);
    }

    public ExtractionException(String message, Throwable cause, boolean transientFailure) {
        this(message, cause, transientFailure, null);
    }

    public ExtractionException(String message, Throwable cause, boolean transientFailure, Duration retryAfter) {
        super(message, cause, transientFailure, retryAfter);
    }
}
