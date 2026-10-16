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
     * Present on copies: imported from Explore, or shared by the learner's teacher.
     *
     * @param shareId the Explore share it was imported from; null once the sharer withdrew it, and for class shares
     * @param kind    EXPLORE (imported) or CLASS (shared by the learner's teacher)
     */
    public record Origin(UUID shareId, String sharedBy, Instant importedAt, String kind) {
    }

    public static DocumentResponse from(Document d) {
        return new DocumentResponse(d.getId(), d.getOriginalFilename(), d.getTitle(), d.getContentType(),
                d.getSizeBytes(), d.getStatus(), d.getEngine(), d.getPageCount(), d.getTextPages(), d.getOcrPages(),
                d.getErrorMessage(), d.getCreatedAt(), d.getCompletedAt(), d.getRetryAt(),
                d.isImported() ? new Origin(d.getOriginShareId(), d.getOriginOwnerName(), d.getImportedAt(),
                        d.getOriginKind() == null ? "EXPLORE" : d.getOriginKind()) : null,
                d.getKind(), d.getDurationSeconds());
    }
}
