package com.sapio.document;

import java.time.Duration;
import java.util.Optional;

/**
 * A pipeline stage (extraction, indexing, analysis) could not finish. The message is safe to show to the document
 * owner. {@link #isTransient()}: the failure is not the document's fault (AI busy, out of quota, misconfigured),
 * so processing resumes automatically later; {@link #retryAfter()}: when that is expected to succeed.
 */
public abstract class PipelineException extends Exception {

    private final boolean transientFailure;
    private final Duration retryAfter;

    protected PipelineException(String message, Throwable cause, boolean transientFailure, Duration retryAfter) {
        super(message, cause);
        this.transientFailure = transientFailure;
        this.retryAfter = retryAfter;
    }

    public boolean isTransient() {
        return transientFailure;
    }

    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
