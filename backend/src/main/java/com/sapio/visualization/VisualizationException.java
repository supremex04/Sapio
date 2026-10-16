package com.sapio.visualization;

/** A step of a visualization failed; {@code message} is safe to show, {@code retryable}: trying again may work. */
public class VisualizationException extends Exception {

    private final boolean retryable;

    public VisualizationException(String message, boolean retryable, Throwable cause) {
        super(message, cause);
        this.retryable = retryable;
    }

    public boolean isRetryable() {
        return retryable;
    }
}
