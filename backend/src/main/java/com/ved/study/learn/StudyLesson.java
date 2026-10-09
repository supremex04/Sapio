package com.ved.study.learn;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/** Generated teaching content for a topic. Shared by all learners of the material; checks are StudyQuestions. */
@Entity
@Table(name = "study_lessons")
public class StudyLesson {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "topic_id", nullable = false, unique = true, updatable = false)
    private UUID topicId;

    /** JSON array of {@link LessonSection}. Stored as text: always read and written whole. */
    @Column(name = "sections_json", nullable = false)
    private String sectionsJson;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "key_takeaways", nullable = false)
    private List<String> keyTakeaways;

    private String model;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected StudyLesson() {
        // for JPA
    }

    StudyLesson(UUID topicId, String sectionsJson, List<String> keyTakeaways, String model, Instant createdAt) {
        this.topicId = topicId;
        this.sectionsJson = sectionsJson;
        this.keyTakeaways = List.copyOf(keyTakeaways);
        this.model = model;
        this.createdAt = createdAt;
    }

    public UUID getId() {
        return id;
    }

    public UUID getTopicId() {
        return topicId;
    }

    String getSectionsJson() {
        return sectionsJson;
    }

    public List<String> getKeyTakeaways() {
        return keyTakeaways;
    }
}
