package com.sapio.progress;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

interface DailyActivityRepository extends JpaRepository<DailyActivity, DailyActivity.Key> {

    /** Atomically adds deltas to the day's counters, creating the row if needed (safe under concurrency). */
    @Modifying
    @Query(nativeQuery = true, value = """
            INSERT INTO user_daily_activity
                (user_id, day, active_seconds, logins, lessons_completed, questions_answered, correct_answers, xp_earned)
            VALUES (:userId, :day, :activeSeconds, :logins, :lessons, :questions, :correct, :xp)
            ON CONFLICT (user_id, day) DO UPDATE SET
                active_seconds     = user_daily_activity.active_seconds + EXCLUDED.active_seconds,
                logins             = user_daily_activity.logins + EXCLUDED.logins,
                lessons_completed  = user_daily_activity.lessons_completed + EXCLUDED.lessons_completed,
                questions_answered = user_daily_activity.questions_answered + EXCLUDED.questions_answered,
                correct_answers    = user_daily_activity.correct_answers + EXCLUDED.correct_answers,
                xp_earned          = user_daily_activity.xp_earned + EXCLUDED.xp_earned""")
    void add(UUID userId, LocalDate day, int activeSeconds, int logins, int lessons, int questions, int correct,
             int xp);

    /** Flags the goal as reached; returns 1 only for the first caller of the day. */
    @Modifying
    @Query(nativeQuery = true, value = """
            UPDATE user_daily_activity SET goal_reached = TRUE
            WHERE user_id = :userId AND day = :day AND goal_reached = FALSE AND active_seconds >= :goalSeconds""")
    int markGoalReached(UUID userId, LocalDate day, int goalSeconds);

    List<DailyActivity> findByIdUserIdAndIdDayBetweenOrderByIdDay(UUID userId, LocalDate from, LocalDate to);

    @Query("""
            select d.id.day from DailyActivity d
            where d.id.userId = :userId and (d.xpEarned > 0 or d.activeSeconds >= :minActiveSeconds)""")
    List<LocalDate> findActiveDays(UUID userId, int minActiveSeconds);

    @Query("""
            select new com.sapio.progress.ProgressTotals(coalesce(sum(d.xpEarned), 0), coalesce(sum(d.activeSeconds), 0),
                   coalesce(sum(d.lessonsCompleted), 0), coalesce(sum(d.questionsAnswered), 0),
                   coalesce(sum(d.correctAnswers), 0))
            from DailyActivity d where d.id.userId = :userId""")
    ProgressTotals totals(UUID userId);
}
