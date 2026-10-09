package com.ved.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String engine,
        Integer pageCount,
        String errorMessage,
        Instant createdAt,
        Instant completedAt) {

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getContentType(), d.getSizeBytes(),
                d.getStatus(), d.getEngine(), d.getPageCount(), d.getErrorMessage(), d.getCreatedAt(),
                d.getCompletedAt());
    }
}
