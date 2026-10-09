package com.ved.study.question;

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

/** One answer by one learner. Immutable; the basis for mastery, calibration and analytics. */
@Entity
@Table(name = "question_attempts")
public class QuestionAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private UUID userId;

    @Column(name = "question_id", nullable = false, updatable = false)
    private UUID questionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private AttemptContext context;

    @Column(name = "assessment_id", updatable = false)
    private UUID assessmentId;

    @Column(name = "selected_option", updatable = false)
    private Integer selectedOption;

    @Column(name = "answer_text", updatable = false)
    private String answerText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, updatable = false)
    private Verdict verdict;

    @Column(nullable = false, updatable = false)
    private double score;

    @Enumerated(EnumType.STRING)
    @Column(updatable = false)
    private Confidence confidence;

    @Column(nullable = false, updatable = false)
    private String feedback;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    protected QuestionAttempt() {
        // for JPA
    }

    QuestionAttempt(UUID userId, UUID questionId, AttemptContext context, UUID assessmentId, Integer selectedOption,
                    String answerText, Verdict verdict, double score, Confidence confidence, String feedback,
                    Instant createdAt) {
        this.userId = userId;
        this.questionId = questionId;
        this.context = context;
        this.assessmentId = assessmentId;
        this.selectedOption = selectedOption;
        this.answerText = answerText;
        this.verdict = verdict;
        this.score = score;
        this.confidence = confidence;
        this.feedback = feedback;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getQuestionId() {
        return questionId;
    }

    public Integer getSelectedOption() {
        return selectedOption;
    }

    public String getAnswerText() {
        return answerText;
    }

    public Verdict getVerdict() {
        return verdict;
    }

    public double getScore() {
        return score;
    }

    public Confidence getConfidence() {
        return confidence;
    }

    public String getFeedback() {
        return feedback;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
