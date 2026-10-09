package com.ved.study.question;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** A learner's spaced-repetition state for one question (Leitner box + due time). */
@Entity
@Table(name = "review_cards")
public class ReviewCard {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(nullable = false)
    private int box;

    @Column(name = "due_at", nullable = false)
    private Instant dueAt;

    @Column(name = "last_reviewed_at")
    private Instant lastReviewedAt;

    @Column(nullable = false)
    private int lapses;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected ReviewCard() {
        // for JPA
    }

    public ReviewCard(UUID userId, UUID questionId, ReviewSchedule.Slot slot, Instant now) {
        this.userId = userId;
        this.questionId = questionId;
        this.box = slot.box();
        this.dueAt = slot.dueAt();
        this.createdAt = now;
    }

    public void reschedule(ReviewSchedule.Slot slot, boolean lapsed, Instant now) {
        this.box = slot.box();
        this.dueAt = slot.dueAt();
        this.lastReviewedAt = now;
        if (lapsed) {
            lapses++;
        }
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public int getBox() {
        return box;
    }

    public Instant getDueAt() {
        return dueAt;
    }
}
