package com.ved.study.question;

import java.time.Instant;
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

/**
 * A question grounded in a topic's source chunks. One bank serves lesson checks, assessments and
 * spaced review. The answer key never leaves the server before the learner answers.
 */
@Entity
@Table(name = "study_questions")
public class StudyQuestion {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "material_id", nullable = false, updatable = false)
    private UUID materialId;

    @Column(name = "topic_id", nullable = false, updatable = false)
    private UUID topicId;

    @Column(name = "lesson_id", updatable = false)
    private UUID lessonId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionOrigin origin;

    /** For lesson checks: index of the section it follows (-1 = warm-up before the first section). */
    @Column(name = "after_section")
    private Integer afterSection;

    @Column(nullable = false)
    private int position;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuestionType type;

    @Column(nullable = false)
    private String prompt;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> options;

    @Column(name = "correct_option")
    private Integer correctOption;

    @Column(name = "reference_answer")
    private String referenceAnswer;

    @Column(nullable = false)
    private String explanation;

    /** Tree nodes that support the answer (for grading context and "see section" links). */
    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "source_node_ids", nullable = false)
    private List<UUID> sourceNodeIds;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudyQuestion() {
        // for JPA
    }

    public StudyQuestion(UUID materialId, UUID topicId, UUID lessonId, QuestionOrigin origin, Integer afterSection,
                         int position, QuestionType type, String prompt, List<String> options, Integer correctOption,
                         String referenceAnswer, String explanation, List<UUID> sourceNodeIds,
                         Instant createdAt) {
        if (type != QuestionType.SHORT_ANSWER
                && (correctOption == null || correctOption < 0 || correctOption >= options.size())) {
            throw new IllegalArgumentException("Choice question needs a valid correct option");
        }
        this.materialId = materialId;
        this.topicId = topicId;
        this.lessonId = lessonId;
        this.origin = origin;
        this.afterSection = afterSection;
        this.position = position;
        this.type = type;
        this.prompt = prompt;
        this.options = List.copyOf(options);
        this.correctOption = type == QuestionType.SHORT_ANSWER ? null : correctOption;
        this.referenceAnswer = referenceAnswer;
        this.explanation = explanation;
        this.sourceNodeIds = List.copyOf(sourceNodeIds);
        this.createdAt = createdAt;
    }

    public boolean isChoice() {
        return type != QuestionType.SHORT_ANSWER;
    }

    /** Text of the correct answer, for reports and AI feedback. */
    public String correctAnswerText() {
        return isChoice() ? options.get(correctOption) : referenceAnswer;
    }

    public UUID getId() {
        return id;
    }

    public UUID getMaterialId() {
        return materialId;
    }

    public UUID getTopicId() {
        return topicId;
    }

    public UUID getLessonId() {
        return lessonId;
    }

    public QuestionOrigin getOrigin() {
        return origin;
    }

    public Integer getAfterSection() {
        return afterSection;
    }

    public int getPosition() {
        return position;
    }

    public QuestionType getType() {
        return type;
    }

    public String getPrompt() {
        return prompt;
    }

    public List<String> getOptions() {
        return options;
    }

    public Integer getCorrectOption() {
        return correctOption;
    }

    public String getReferenceAnswer() {
        return referenceAnswer;
    }

    public String getExplanation() {
        return explanation;
    }

    public List<UUID> getSourceNodeIds() {
        return sourceNodeIds;
    }
}
