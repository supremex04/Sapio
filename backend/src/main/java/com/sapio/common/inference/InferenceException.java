package com.sapio.common.inference;

import java.time.Duration;
import java.util.Optional;

/**
 * A call to the inference service failed, classified by the error envelope of the contract
 * ({@code code}, {@code message}, {@code retryable}; docs/specs/inference-api.md). Callers branch on
 * {@link #kind()} / {@link #isRetryable()}, never on message text.
 */
public class InferenceException extends Exception {

    /** Why the call failed, which decides who can fix it. */
    public enum Kind {
        /** The input itself cannot be processed (corrupt file, too little text). Message is user-safe. */
        INPUT_REJECTED,
        /** A model or engine is busy, out of quota or unreachable. Message is user-safe; retry later. */
        UNAVAILABLE,
        /** The service is misconfigured (keys, extras, routes). An operator must fix it. */
        MISCONFIGURED,
        /** Request or response does not match the contract: a bug or version skew between the services. */
        CONTRACT_VIOLATION,
        /** Unexpected failure inside the service. */
        INTERNAL;

        /** Only these carry a message written for end users. */
        boolean userSafe() {
            return this == INPUT_REJECTED || this == UNAVAILABLE;
        }
    }

    private final Kind kind;
    private final String code;
    private final boolean retryable;
    private final Duration retryAfter;
    private final String requestId;

    public InferenceException(Kind kind, String code, String message, boolean retryable, Duration retryAfter,
                              String requestId, Throwable cause) {
        super(message, cause);
        this.kind = kind;
        this.code = code;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
        this.requestId = requestId;
    }

    public Kind kind() {
        return kind;
    }

    /** The wire code (e.g. {@code MODEL_UNAVAILABLE}), for logs. */
    public String code() {
        return code;
    }

    /** The same request may succeed later without changes. */
    public boolean isRetryable() {
        return retryable;
    }

    public boolean isInputRejected() {
        return kind == Kind.INPUT_REJECTED;
    }

    /** When the service expects to serve again (a quota reset), if it said so. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }

    public Optional<String> requestId() {
        return Optional.ofNullable(requestId);
    }

    /**
     * The message to show a learner: the service's own text when it was written for users, otherwise
     * {@code fallback} (operator details such as missing keys or schema errors never reach the UI).
     */
    public String userMessage(String fallback) {
        String message = getMessage();
        return kind.userSafe() && message != null && !message.isBlank() ? message : fallback;
    }
}
