package com.sapio.common.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.sapio.common.web.RequestId;

/**
 * Uniform error body for every non-2xx API response. {@code requestId} matches the {@code X-Request-Id}
 * response header and the server logs (quote it in bug reports).
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        ErrorCode code,
        String message,
        String path,
        Instant timestamp,
        List<FieldViolation> fieldErrors,
        String requestId) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path) {
        return of(code, message, path, List.of());
    }

    /** @param fieldErrors per-field validation messages (empty otherwise); the request id comes from the MDC */
    public static ApiError of(ErrorCode code, String message, String path, List<FieldViolation> fieldErrors) {
        return new ApiError(code.status().value(), code, message, path, Instant.now(), fieldErrors,
                RequestId.current().orElse(null));
    }
}
