package com.sapio.study.material;

import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "study_topics")
public class StudyTopic {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false)
    private String summary;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_concepts", nullable = false)
    private List<String> keyConcepts;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicDifficulty difficulty;

    /** Document-tree nodes (chapters/sections) this topic covers; lessons and questions are grounded in them. */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "node_ids", nullable = false)
    private List<UUID> nodeIds;

    protected StudyTopic() {
        // for JPA
    }

    public StudyTopic(UUID materialId, int position, String title, String summary, List<String> keyConcepts,
                      TopicDifficulty difficulty, List<UUID> nodeIds) {
        this.materialId = materialId;
        this.position = position;
        this.title = title;
        this.summary = summary;
        this.keyConcepts = List.copyOf(keyConcepts);
        this.difficulty = difficulty;
        this.nodeIds = List.copyOf(nodeIds);
    }

    public UUID getId() {
        return id;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public int getPosition() {
        return position;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public List<String> getKeyConcepts() {
        return keyConcepts;
    }

    public TopicDifficulty getDifficulty() {
        return difficulty;
    }

    public List<UUID> getNodeIds() {
        return nodeIds;
    }
}
