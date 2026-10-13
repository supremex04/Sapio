package com.sapio.common.error;

/**
 * Base exception for expected, client-facing failures. The {@link GlobalExceptionHandler} turns it into
 * an {@link ApiError} using the {@link ErrorCode}'s HTTP status.
 */
public class ApplicationException extends RuntimeException {

    private final ErrorCode code;

    public ApplicationException(ErrorCode code, String message) {
        super(message);
        this.code = code;
    }

    public ErrorCode code() {
        return code;
    }

    public static ApplicationException notFound(String what) {
        return new ApplicationException(ErrorCode.NOT_FOUND, what + " not found");
    }
}
