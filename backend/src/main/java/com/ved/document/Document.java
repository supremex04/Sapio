package com.ved.document;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Basic;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * An uploaded file and the outcome of converting it to Markdown. State changes go through the
 * methods below so the lifecycle rules live in one place.
 */
@Entity
@Table(name = "documents")
public class Document {

    private static final int MAX_ERROR_LENGTH = 1000;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(name = "original_filename", nullable = false)
    private String originalFilename;

    @Column(name = "content_type", nullable = false)
    private String contentType;

    @Column(name = "size_bytes", nullable = false)
    private long sizeBytes;

    @Column(name = "storage_key", nullable = false)
    private String storageKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private DocumentStatus status;

    private String engine;

    @Column(name = "page_count")
    private Integer pageCount;

    @Basic(fetch = FetchType.LAZY)
    @Column(name = "result_markdown")
    private String resultMarkdown;

    @Column(name = "error_message")
    private String errorMessage;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "processing_started_at")
    private Instant processingStartedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Document() {
        // for JPA
    }

    public Document(UUID ownerId, String originalFilename, String contentType, long sizeBytes, String storageKey) {
        this.ownerId = ownerId;
        this.originalFilename = originalFilename;
        this.contentType = contentType;
        this.sizeBytes = sizeBytes;
        this.storageKey = storageKey;
        this.status = DocumentStatus.PENDING;
    }

    public void markProcessing(Instant now) {
        this.status = DocumentStatus.PROCESSING;
        this.processingStartedAt = now;
        this.errorMessage = null;
    }

    public void markCompleted(String markdown, String engine, Integer pageCount, Instant now) {
        this.status = DocumentStatus.COMPLETED;
        this.resultMarkdown = markdown;
        this.engine = engine;
        this.pageCount = pageCount;
        this.completedAt = now;
    }

    public void markFailed(String reason, Instant now) {
        this.status = DocumentStatus.FAILED;
        this.errorMessage = reason == null || reason.length() <= MAX_ERROR_LENGTH
                ? reason : reason.substring(0, MAX_ERROR_LENGTH);
        this.completedAt = now;
    }

    /** Puts an interrupted job (e.g. after a restart) back in the queue. */
    public void requeue() {
        this.status = DocumentStatus.PENDING;
        this.processingStartedAt = null;
    }

    public UUID getId() {
        return id;
    }

    public UUID getOwnerId() {
        return ownerId;
    }

    public String getOriginalFilename() {
        return originalFilename;
    }

    public String getContentType() {
        return contentType;
    }

    public long getSizeBytes() {
        return sizeBytes;
    }

    public String getStorageKey() {
        return storageKey;
    }

    public DocumentStatus getStatus() {
        return status;
    }

    public String getEngine() {
        return engine;
    }

    public Integer getPageCount() {
        return pageCount;
    }

    public String getResultMarkdown() {
        return resultMarkdown;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
