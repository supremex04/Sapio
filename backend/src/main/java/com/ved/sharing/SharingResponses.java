package com.ved.sharing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Wire records of the sharing API (docs/specs/sharing.md). */
final class SharingResponses {

    private SharingResponses() {
    }

    /** @param emoji illustration name known to the frontend */
    record Category(String slug, String name, String emoji) {
    }

    record ShareRequest(@NotBlank String category, @Size(max = 500) String description) {
    }

    /** The sharer's view of one of their shares. */
    record MyShare(UUID id, UUID documentId, Category category, String description, int importCount,
                   Instant sharedAt, Instant updatedAt) {
    }

    /**
     * One card in Explore.
     *
     * @param mine               shared by the viewer (they cannot import it)
     * @param importedDocumentId the viewer's copy, when they already imported it
     */
    record ShareSummary(UUID id, String title, String summary, Category category, String kind, String language,
                        int pageCount, int topicCount, String sharedBy, Instant sharedAt, int importCount,
                        boolean mine, UUID importedDocumentId) {
    }

    record ShareDetail(ShareSummary share, String description, List<String> topics, Instant updatedAt) {
    }

    /** @param alreadyImported the viewer had imported it before; {@code documentId} is that existing copy */
    record ImportResult(UUID documentId, boolean alreadyImported) {
    }
}
