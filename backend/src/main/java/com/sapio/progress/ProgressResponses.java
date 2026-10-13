package com.sapio.progress;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/** Response shapes of /api/progress (docs/specs/progress.md). */
final class ProgressResponses {

    private ProgressResponses() {
    }

    record Summary(Xp xp, Streak streak, Goal goal, Day today, Totals totals) {
    }

    record Xp(long total, int level, long levelStartXp, long nextLevelXp) {
    }

    record Streak(int current, int longest, boolean activeToday) {
    }

    record Goal(int dailyMinutes, int todayMinutes, boolean reached) {
    }

    record Day(LocalDate date, int activeSeconds, int logins, int lessonsCompleted, int questionsAnswered,
               int correctAnswers, int xpEarned) {

        static Day empty(LocalDate date) {
            return new Day(date, 0, 0, 0, 0, 0, 0);
        }

        static Day of(DailyActivity d) {
            return new Day(d.getDay(), d.getActiveSeconds(), d.getLogins(), d.getLessonsCompleted(),
                    d.getQuestionsAnswered(), d.getCorrectAnswers(), d.getXpEarned());
        }
    }

    record Totals(long activeSeconds, long lessonsCompleted, long questionsAnswered, long correctAnswers,
                  long assessmentsCompleted) {
    }

    record DailySeries(List<Day> days) {
    }

    record ActivityItem(String type, UUID documentId, String documentTitle, String topicTitle, int xp,
                        Instant occurredAt) {
    }

    record ActivityFeed(List<ActivityItem> items) {
    }

    record GoalRequest(int dailyMinutes) {
    }

    record GoalResponse(int dailyMinutes) {
    }

    record HeartbeatRequest(String timeZone) {
    }
}
