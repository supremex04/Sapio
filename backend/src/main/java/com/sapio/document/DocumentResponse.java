package com.sapio.document;

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
        Instant completedAt,
        Instant retryAt,
        Origin origin,
        SupportedFileType.Kind kind,
        Integer durationSeconds) {

    /**
     * Present on documents imported from Explore.
     *
     * @param shareId the share it was imported from; null once the sharer withdrew it
     */
    public record Origin(UUID shareId, String sharedBy, Instant importedAt) {
    }

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getTitle(), d.getContentType(),
                d.getSizeBytes(), d.getStatus(), d.getEngine(), d.getPageCount(), d.getTextPages(), d.getOcrPages(),
                d.getErrorMessage(), d.getCreatedAt(), d.getCompletedAt(), d.getRetryAt(),
                d.isImported() ? new Origin(d.getOriginShareId(), d.getOriginOwnerName(), d.getImportedAt()) : null,
                d.getKind(), d.getDurationSeconds());
    }
}
