package com.ved.progress;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

/** Per-user, per-local-day counters. Written only through atomic upserts in {@link DailyActivityRepository}. */
@Entity
@Table(name = "user_daily_activity")
public class DailyActivity {

    @Embeddable
    public record Key(@Column(name = "user_id") UUID userId, @Column(name = "day") LocalDate day)
            implements Serializable {
    }

    @EmbeddedId
    private Key id;

    @Column(name = "active_seconds", nullable = false)
    private int activeSeconds;

    @Column(nullable = false)
    private int logins;

    @Column(name = "lessons_completed", nullable = false)
    private int lessonsCompleted;

    @Column(name = "questions_answered", nullable = false)
    private int questionsAnswered;

    @Column(name = "correct_answers", nullable = false)
    private int correctAnswers;

    @Column(name = "xp_earned", nullable = false)
    private int xpEarned;

    @Column(name = "goal_reached", nullable = false)
    private boolean goalReached;

    protected DailyActivity() {
        // for JPA
    }

    public LocalDate getDay() {
        return id.day();
    }

    public int getActiveSeconds() {
        return activeSeconds;
    }

    public int getLogins() {
        return logins;
    }

    public int getLessonsCompleted() {
        return lessonsCompleted;
    }

    public int getQuestionsAnswered() {
        return questionsAnswered;
    }

    public int getCorrectAnswers() {
        return correctAnswers;
    }

    public int getXpEarned() {
        return xpEarned;
    }

    public boolean isGoalReached() {
        return goalReached;
    }
}
