package com.ved.study.material;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The learning layer of a document: its title, language and the topics of the learning path. Topics point
 * at nodes of the document tree (knowledge store); Learn and Test both read from that same representation.
 */
@Entity
@Table(name = "study_materials")
public class StudyMaterial {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "document_id", nullable = false, updatable = false)
    private UUID documentId;

    @Column(name = "owner_id", nullable = false, updatable = false)
    private UUID ownerId;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @Column(nullable = false)
    private String language;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MaterialKind kind;

    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudyMaterial() {
        // for JPA
    }

    public StudyMaterial(UUID documentId, UUID ownerId, String title, String summary, String language,
                         MaterialKind kind, String model, Instant createdAt) {
        this.documentId = documentId;
        this.ownerId = ownerId;
        this.title = title;
        this.summary = summary;
        this.language = language;
        this.kind = kind;
        this.model = model;
        this.createdAt = createdAt;
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

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getLanguage() {
        return language;
    }

    public MaterialKind getKind() {
        return kind;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
