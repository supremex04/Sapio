package com.ved.common.inference;

/**
 * A call to the inference service failed. {@link #isClientError()} means the input was rejected
 * (4xx, not worth retrying); otherwise the service or a model behind it was unavailable.
 */
public class InferenceException extends Exception {

    private final boolean clientError;

    public InferenceException(String message, boolean clientError, Throwable cause) {
        super(message, cause);
        this.clientError = clientError;
    }

    public boolean isClientError() {
        return clientError;
    }
}
