package com.sapio.study.assess;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "assessment_items")
public class AssessmentItem {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "assessment_id", nullable = false, updatable = false)
    private UUID assessmentId;

    @Column(nullable = false, updatable = false)
    private int position;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Column(name = "attempt_id")
    private UUID attemptId;

    protected AssessmentItem() {
        // for JPA
    }

    AssessmentItem(UUID assessmentId, int position, UUID questionId) {
        this.assessmentId = assessmentId;
        this.position = position;
        this.questionId = questionId;
    }

    boolean isAnswered() {
        return attemptId != null;
    }

    void answer(UUID attemptId) {
        this.attemptId = attemptId;
    }

    public int getPosition() {
        return position;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public UUID getAttemptId() {
        return attemptId;
    }
}
