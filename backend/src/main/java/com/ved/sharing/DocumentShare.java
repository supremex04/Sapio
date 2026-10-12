package com.ved.sharing;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * A document published to Explore. The listing fields are a snapshot of the document's study material, taken when
 * it was shared (and refreshed when the share is updated), so Explore never reads the sharer's live data.
 */
@Entity
@Table(name = "document_shares")
public class DocumentShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String category;

    @Column(nullable = false)
    private String description;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String kind;

    @Column(nullable = false)
    private String language;

    @Column(name = "page_count", nullable = false)
    private int pageCount;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "topic_titles", nullable = false)
    private List<String> topicTitles;

    /** Maintained by an atomic UPDATE after each import, never through this entity. */
    @Column(name = "import_count", nullable = false, insertable = false, updatable = false)
    private int importCount;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected DocumentShare() {
        // for JPA
    }

    DocumentShare(UUID documentId, UUID ownerId, Instant now) {
        this.documentId = documentId;
        this.ownerId = ownerId;
        this.createdAt = now;
    }

    /** What the sharer chose, and the snapshot shown in Explore. */
    void publish(String category, String description, Snapshot snapshot, Instant now) {
        this.category = category;
        this.description = description;
        this.title = snapshot.title();
        this.summary = snapshot.summary();
        this.kind = snapshot.kind();
        this.language = snapshot.language();
        this.pageCount = snapshot.pageCount();
        this.topicTitles = List.copyOf(snapshot.topicTitles());
        this.updatedAt = now;
    }

    record Snapshot(String title, String summary, String kind, String language, int pageCount,
                    List<String> topicTitles) {
    }

    public UUID getId() {
        return id;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getCategory() {
        return category;
    }

    public String getDescription() {
        return description;
    }

    public String getTitle() {
        return title;
    }

    public int getImportCount() {
        return importCount;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
