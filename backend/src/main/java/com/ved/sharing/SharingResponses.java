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
    record MyShare(UUID id, UUID documentId, Category category, String description, int importCount, int upvotes,
                   int downvotes, int commentCount, Instant sharedAt, Instant updatedAt) {
    }

    /** @param myVote the viewer's vote: 1, -1 or 0 (none) */
    record Votes(int upvotes, int downvotes, int myVote) {
    }

    /** @param value 1 (up), -1 (down) or 0 (take the vote back) */
    record VoteRequest(int value) {
    }

    record CommentRequest(String body) {
    }

    /**
     * @param bySharer  written by the person who shared the document (shown as a badge)
     * @param canDelete the viewer wrote it, or shared the document
     */
    record Comment(UUID id, String body, String author, Instant createdAt, boolean bySharer, boolean mine,
                   boolean canDelete) {
    }

    /**
     * One card in Explore.
     *
     * @param mine               shared by the viewer (they cannot import it)
     * @param importedDocumentId the viewer's copy, when they already imported it
     */
    record ShareSummary(UUID id, String title, String summary, Category category, String kind, String language,
                        int pageCount, int topicCount, String sharedBy, Instant sharedAt, int importCount,
                        Votes votes, int commentCount, boolean mine, UUID importedDocumentId) {
    }

    record ShareDetail(ShareSummary share, String description, List<String> topics, Instant updatedAt) {
    }

    /** @param alreadyImported the viewer had imported it before; {@code documentId} is that existing copy */
    record ImportResult(UUID documentId, boolean alreadyImported) {
    }
}
