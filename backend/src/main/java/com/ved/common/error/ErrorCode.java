package com.ved.common.error;

import org.springframework.http.HttpStatus;

/**
 * Stable, machine-readable error codes returned to API clients. Add new codes here rather than
 * inventing ad-hoc strings, so the frontend can rely on them.
 */
public enum ErrorCode {
    VALIDATION_FAILED(HttpStatus.BAD_REQUEST),
    UNAUTHENTICATED(HttpStatus.UNAUTHORIZED),
    INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED),
    INVALID_REFRESH_TOKEN(HttpStatus.UNAUTHORIZED),
    ACCESS_DENIED(HttpStatus.FORBIDDEN),
    NOT_FOUND(HttpStatus.NOT_FOUND),
    EMAIL_ALREADY_REGISTERED(HttpStatus.CONFLICT),
    RESULT_NOT_READY(HttpStatus.CONFLICT),
    MATERIAL_NOT_READY(HttpStatus.CONFLICT),
    ALREADY_ANSWERED(HttpStatus.CONFLICT),
    ASSESSMENT_CLOSED(HttpStatus.CONFLICT),
    NOT_DUE(HttpStatus.CONFLICT),
    INVALID_STATE(HttpStatus.CONFLICT),
    FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE),
    RATE_LIMITED(HttpStatus.TOO_MANY_REQUESTS),
    UNSUPPORTED_FILE_TYPE(HttpStatus.UNSUPPORTED_MEDIA_TYPE),
    AI_INPUT_REJECTED(HttpStatus.UNPROCESSABLE_CONTENT),
    AI_UNAVAILABLE(HttpStatus.SERVICE_UNAVAILABLE),
    INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR);

    private final HttpStatus status;

    ErrorCode(HttpStatus status) {
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
