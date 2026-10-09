package com.ved.common.error;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Uniform error body for every non-2xx API response.
 */
@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ApiError(
        int status,
        ErrorCode code,
        String message,
        String path,
        Instant timestamp,
        List<FieldViolation> fieldErrors) {

    public record FieldViolation(String field, String message) {
    }

    public static ApiError of(ErrorCode code, String message, String path) {
        return new ApiError(code.status().value(), code, message, path, Instant.now(), List.of());
    }
}
