package com.sapio.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** Append-only analytics log of learning actions (with the XP each earned). */
@Entity
@Table(name = "learning_events")
public class LearningEventRecord {

    public static final String GOAL_REACHED = "DAILY_GOAL_REACHED";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(nullable = false, updatable = false)
    private String type;

    @Column(name = "document_id", updatable = false)
    private UUID documentId;

    @Column(name = "document_title", updatable = false)
    private String documentTitle;

    @Column(name = "topic_id", updatable = false)
    private UUID topicId;

    @Column(name = "topic_title", updatable = false)
    private String topicTitle;

    @Column(name = "assessment_id", updatable = false)
    private UUID assessmentId;

    @Column(name = "question_id", updatable = false)
    private UUID questionId;

    @Column(updatable = false)
    private String verdict;

    @Column(updatable = false)
    private String confidence;

    @Column(nullable = false, updatable = false)
    private int xp;

    @Column(name = "local_date", nullable = false, updatable = false)
    private LocalDate localDate;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    protected LearningEventRecord() {
        // for JPA
    }

    LearningEventRecord(UUID userId, String type, UUID documentId, String documentTitle, UUID topicId,
                        String topicTitle, UUID assessmentId, UUID questionId, String verdict, String confidence,
                        int xp, LocalDate localDate, Instant occurredAt) {
        this.userId = userId;
        this.type = type;
        this.documentId = documentId;
        this.documentTitle = documentTitle;
        this.topicId = topicId;
        this.topicTitle = topicTitle;
        this.assessmentId = assessmentId;
        this.questionId = questionId;
        this.verdict = verdict;
        this.confidence = confidence;
        this.xp = xp;
        this.localDate = localDate;
        this.occurredAt = occurredAt;
    }

    public String getType() {
        return type;
    }

    public UUID getDocumentId() {
        return documentId;
    }

    public String getDocumentTitle() {
        return documentTitle;
    }

    public String getTopicTitle() {
        return topicTitle;
    }

    public int getXp() {
        return xp;
    }

    public Instant getOccurredAt() {
        return occurredAt;
    }
}
