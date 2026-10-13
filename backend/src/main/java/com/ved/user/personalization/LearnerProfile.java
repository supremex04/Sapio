package com.ved.user.personalization;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

/**
 * Who a learner is and how they like to learn ("Personalize"). Every field is optional; content generated for them
 * (lessons, notes, questions, tutor answers) is tailored to what is set. Grading never depends on it.
 */
@Entity
@Table(name = "learner_profiles")
public class LearnerProfile {

    public enum AcademicLevel { SCHOOL, HIGHER_SECONDARY, UNDERGRADUATE, POSTGRADUATE, DOCTORAL, NONE }

    public enum Occupation { STUDENT, PROFESSIONAL, EDUCATOR, SELF_LEARNER }

    public enum Depth { CONCISE, BALANCED, IN_DEPTH }

    /** Evidence-compatible preferences (not "learning styles", which do not improve learning when matched). */
    public enum Preference { EXAMPLES, ANALOGIES, APPLICATIONS, FORMAL, CODE, STEP_BY_STEP }

    public enum Goal { EXAM, UNDERSTANDING, WORK, CURIOSITY }

    @Id
    @Column(name = "user_id")
    private UUID userId;

    @Enumerated(EnumType.STRING)
    @Column(name = "academic_level")
    private AcademicLevel academicLevel;

    @Enumerated(EnumType.STRING)
    private Occupation occupation;

    @Column(name = "field_of_study")
    private String fieldOfStudy;

    @Enumerated(EnumType.STRING)
    private Depth depth;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(nullable = false)
    private List<String> preferences;

    @Enumerated(EnumType.STRING)
    private Goal goal;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected LearnerProfile() {
        // for JPA
    }

    LearnerProfile(UUID userId) {
        this.userId = userId;
        this.preferences = List.of();
    }

    void update(AcademicLevel academicLevel, Occupation occupation, String fieldOfStudy, Depth depth,
                List<Preference> preferences, Goal goal, Instant now) {
        this.academicLevel = academicLevel;
        this.occupation = occupation;
        String field = fieldOfStudy == null ? "" : fieldOfStudy.strip();
        this.fieldOfStudy = field.isEmpty() ? null : field.substring(0, Math.min(field.length(), 100));
        this.depth = depth;
        this.preferences = preferences == null ? List.of()
                : preferences.stream().distinct().map(Enum::name).toList();
        this.goal = goal;
        this.updatedAt = now;
    }

    /**
     * A short fingerprint of everything that shapes generated content. Content records the signature it was written
     * for; a different signature means "written for an earlier profile".
     */
    public String signature() {
        String canonical = String.join("|", String.valueOf(academicLevel), String.valueOf(occupation),
                String.valueOf(fieldOfStudy), String.valueOf(depth),
                String.join(",", preferences.stream().sorted().toList()), String.valueOf(goal));
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return "p1-" + HexFormat.of().formatHex(digest, 0, 12);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public boolean isConfigured() {
        return academicLevel != null || occupation != null || fieldOfStudy != null || depth != null
                || !preferences.isEmpty() || goal != null;
    }

    public UUID getUserId() {
        return userId;
    }

    public AcademicLevel getAcademicLevel() {
        return academicLevel;
    }

    public Occupation getOccupation() {
        return occupation;
    }

    public String getFieldOfStudy() {
        return fieldOfStudy;
    }

    public Depth getDepth() {
        return depth;
    }

    public List<Preference> getPreferences() {
        return preferences.stream().map(Preference::valueOf).toList();
    }

    public Goal getGoal() {
        return goal;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
