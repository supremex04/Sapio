package com.ved.common.error;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import com.ved.common.ai.AiException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    /** AI failures carry a retry hint (a quota reset) worth passing on to clients. */
    @ExceptionHandler(AiException.class)
    ResponseEntity<ApiError> handleAi(AiException ex, HttpServletRequest request) {
        ApiError body = ApiError.of(ex.code(), ex.getMessage(), request.getRequestURI());
        return ex.retryAfter()
                .map(after -> ResponseEntity.status(body.status())
                        .header(HttpHeaders.RETRY_AFTER, String.valueOf(Math.max(1, after.toSeconds())))
                        .body(body))
                .orElseGet(() -> respond(body));
    }

    @ExceptionHandler(ApplicationException.class)
    ResponseEntity<ApiError> handleApplication(ApplicationException ex, HttpServletRequest request) {
        return respond(ApiError.of(ex.code(), ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(MethodArgumentNotValidException ex, HttpServletRequest request) {
        List<ApiError.FieldViolation> violations = ex.getBindingResult().getFieldErrors().stream()
                .map(fe -> new ApiError.FieldViolation(fe.getField(), fe.getDefaultMessage()))
                .toList();
        return respond(ApiError.of(ErrorCode.VALIDATION_FAILED, "Request validation failed", request.getRequestURI(),
                violations));
    }

    @ExceptionHandler({
            MissingServletRequestPartException.class,
            MissingServletRequestParameterException.class,
            MethodArgumentTypeMismatchException.class,
            HttpMediaTypeNotSupportedException.class})
    ResponseEntity<ApiError> handleBadRequest(Exception ex, HttpServletRequest request) {
        return respond(ApiError.of(ErrorCode.VALIDATION_FAILED, ex.getMessage(), request.getRequestURI()));
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    ResponseEntity<ApiError> handleTooLarge(HttpServletRequest request) {
        return respond(ApiError.of(ErrorCode.FILE_TOO_LARGE, "Uploaded file is too large", request.getRequestURI()));
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<ApiError> handleAccessDenied(HttpServletRequest request) {
        return respond(ApiError.of(ErrorCode.ACCESS_DENIED, "Access denied", request.getRequestURI()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiError> handleNoResource(HttpServletRequest request) {
        return respond(ApiError.of(ErrorCode.NOT_FOUND, "Resource not found", request.getRequestURI()));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception ex, HttpServletRequest request) {
        log.error("Unhandled error on {} {}", request.getMethod(), request.getRequestURI(), ex);
        return respond(ApiError.of(ErrorCode.INTERNAL_ERROR, "Unexpected error", request.getRequestURI()));
    }

    private static ResponseEntity<ApiError> respond(ApiError body) {
        return ResponseEntity.status(body.status()).body(body);
    }
}
