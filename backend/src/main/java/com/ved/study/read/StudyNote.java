package com.ved.study.read;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Generated study notes for one tree node, cached (shared content, like lessons). */
@Entity
@Table(name = "study_notes")
public class StudyNote {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "node_id", nullable = false, updatable = false)
    private UUID nodeId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private NoteStyle style;

    @Column(nullable = false)
    private String markdown;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "figure_ids", nullable = false)
    private List<UUID> figureIds;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_node_ids", nullable = false)
    private List<UUID> sourceNodeIds;

    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudyNote() {
        // for JPA
    }

    StudyNote(UUID nodeId, NoteStyle style, String markdown, List<UUID> figureIds, List<UUID> sourceNodeIds,
              String model, Instant now) {
        this.nodeId = nodeId;
        this.style = style;
        this.markdown = markdown;
        this.figureIds = List.copyOf(figureIds);
        this.sourceNodeIds = List.copyOf(sourceNodeIds);
        this.model = model;
        this.createdAt = now;
    }

    public NoteStyle getStyle() {
        return style;
    }

    public UUID getNodeId() {
        return nodeId;
    }

    public String getMarkdown() {
        return markdown;
    }

    public List<UUID> getFigureIds() {
        return figureIds;
    }

    public List<UUID> getSourceNodeIds() {
        return sourceNodeIds;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
