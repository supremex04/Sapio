package com.ved.document;

import java.time.Instant;
import java.util.UUID;

public record DocumentResponse(
        UUID id,
        String filename,
        String title,
        String contentType,
        long sizeBytes,
        DocumentStatus status,
        String engine,
        Integer pageCount,
        Integer textPages,
        Integer ocrPages,
        String errorMessage,
        Instant createdAt,
        Instant completedAt) {

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getTitle(), d.getContentType(),
                d.getSizeBytes(), d.getStatus(), d.getEngine(), d.getPageCount(), d.getTextPages(), d.getOcrPages(),
                d.getErrorMessage(), d.getCreatedAt(), d.getCompletedAt());
    }
}
