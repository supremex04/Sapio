package com.ved.common.ai;

import java.time.Duration;
import java.util.Optional;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;

/**
 * An AI port (study content, indexing) could not do its job. Technology-neutral: adapters translate their own
 * failures into it, so features never see HTTP or vendor details. The message is safe to show to users.
 *
 * <p>Rendered as 422 {@code AI_INPUT_REJECTED} when the input itself was refused (retrying will not help),
 * otherwise as 503 {@code AI_UNAVAILABLE} (with {@code Retry-After} when known).
 */
public class AiException extends ApplicationException {

    private final boolean inputRejected;
    private final boolean retryable;
    private final Duration retryAfter;

    public AiException(String message, boolean inputRejected, boolean retryable, Duration retryAfter) {
        super(inputRejected ? ErrorCode.AI_INPUT_REJECTED : ErrorCode.AI_UNAVAILABLE, message);
        this.inputRejected = inputRejected;
        this.retryable = retryable;
        this.retryAfter = retryAfter;
    }

    /** The input (document, question) was refused; retrying the same input will not help. */
    public boolean isInputRejected() {
        return inputRejected;
    }

    /**
     * The same call may succeed later unchanged (busy, out of quota). False for rejected input and for
     * failures an operator must fix (misconfiguration, contract mismatch).
     */
    public boolean isRetryable() {
        return retryable;
    }

    /** When the AI is expected to serve again (e.g. a quota reset), if known. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
