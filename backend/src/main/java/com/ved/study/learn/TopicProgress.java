package com.ved.study.learn;

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
@Table(name = "topic_progress")
public class TopicProgress {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TopicStatus status;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "completed_at")
    private Instant completedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected TopicProgress() {
        // for JPA
    }

    TopicProgress(UUID userId, UUID topicId, Instant now) {
        this.userId = userId;
        this.topicId = topicId;
        this.status = TopicStatus.IN_PROGRESS;
        this.startedAt = now;
        this.updatedAt = now;
    }

    /** @return true if this call completed the topic (false if it was already completed) */
    boolean complete(Instant now) {
        updatedAt = now;
        if (status == TopicStatus.COMPLETED) {
            return false;
        }
        status = TopicStatus.COMPLETED;
        completedAt = now;
        return true;
    }

    void touch(Instant now) {
        updatedAt = now;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public TopicStatus getStatus() {
        return status;
    }

    public Instant getCompletedAt() {
        return completedAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
