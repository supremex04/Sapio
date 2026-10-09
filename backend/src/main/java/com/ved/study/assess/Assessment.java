package com.ved.study.assess;

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

@Entity
@Table(name = "assessments")
public class Assessment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AssessmentDifficulty difficulty;

    @Column(name = "question_count", nullable = false)
    private int questionCount;

    private Double score;

    /** JSON of {@link AssessmentReport}; set on completion. */
    @Column(name = "report_json")
    private String reportJson;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    protected Assessment() {
        // for JPA
    }

    Assessment(UUID userId, UUID materialId, AssessmentDifficulty difficulty, int questionCount, Instant now) {
        this.userId = userId;
        this.materialId = materialId;
        this.difficulty = difficulty;
        this.questionCount = questionCount;
        this.status = AssessmentStatus.IN_PROGRESS;
        this.createdAt = now;
    }

    void complete(double score, String reportJson, Instant now) {
        this.status = AssessmentStatus.COMPLETED;
        this.score = score;
        this.reportJson = reportJson;
        this.completedAt = now;
    }

    public boolean isOpen() {
        return status == AssessmentStatus.IN_PROGRESS;
    }

    public UUID getId() {
        return id;
    }

    public UUID getUserId() {
        return userId;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public AssessmentStatus getStatus() {
        return status;
    }

    public AssessmentDifficulty getDifficulty() {
        return difficulty;
    }

    public int getQuestionCount() {
        return questionCount;
    }

    public Double getScore() {
        return score;
    }

    String getReportJson() {
        return reportJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }
}
