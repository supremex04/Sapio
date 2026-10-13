package com.sapio.progress;

import java.time.Clock;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.sapio.progress.ProgressResponses.ActivityFeed;
import com.sapio.progress.ProgressResponses.ActivityItem;
import com.sapio.progress.ProgressResponses.DailySeries;
import com.sapio.progress.ProgressResponses.Day;
import com.sapio.progress.ProgressResponses.Goal;
import com.sapio.progress.ProgressResponses.Streak;
import com.sapio.progress.ProgressResponses.Summary;
import com.sapio.progress.ProgressResponses.Totals;
import com.sapio.progress.ProgressResponses.Xp;
import com.sapio.study.LearningActivityEvent;
import com.sapio.user.User;
import com.sapio.user.UserService;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Read side of progress: summary (XP, level, streak, goal), daily series, activity feed. */
@Service
class ProgressService {

    /** A day counts toward the streak with any XP, or at least this much active time. */
    static final int STREAK_MIN_ACTIVE_SECONDS = 5 * 60;
    /** Low-signal events kept out of the learner-facing feed. */
    private static final List<String> FEED_EXCLUDED = List.of(
            LearningActivityEvent.Type.LESSON_STARTED.name(), LearningActivityEvent.Type.ASSESSMENT_STARTED.name());

    private final DailyActivityRepository daily;
    private final LearningEventRepository events;
    private final UserService users;
    private final Clock clock;

    ProgressService(DailyActivityRepository daily, LearningEventRepository events, UserService users, Clock clock) {
        this.daily = daily;
        this.events = events;
        this.users = users;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public Summary summary(UUID userId) {
        User user = users.getById(userId);
        LocalDate today = LocalDate.now(clock.withZone(user.zoneId()));
        ProgressTotals totals = daily.totals(userId);
        int level = LevelCurve.levelFor(totals.xp());
        Streaks.Result streak = Streaks.compute(daily.findActiveDays(userId, STREAK_MIN_ACTIVE_SECONDS), today);
        Day todayActivity = daily.findById(new DailyActivity.Key(userId, today)).map(Day::of)
                .orElse(Day.empty(today));
        int todayMinutes = todayActivity.activeSeconds() / 60;
        return new Summary(
                new Xp(totals.xp(), level, LevelCurve.xpToReach(level), LevelCurve.xpToReach(level + 1)),
                new Streak(streak.current(), streak.longest(), streak.activeToday()),
                new Goal(user.getDailyGoalMinutes(), todayMinutes, todayMinutes >= user.getDailyGoalMinutes()),
                todayActivity,
                new Totals(totals.activeSeconds(), totals.lessonsCompleted(), totals.questionsAnswered(),
                        totals.correctAnswers(),
                        events.countByUserIdAndType(userId, LearningActivityEvent.Type.ASSESSMENT_COMPLETED.name())));
    }

    @Transactional(readOnly = true)
    public DailySeries daily(UUID userId, int days) {
        int span = Math.clamp(days, 1, 90);
        LocalDate today = LocalDate.now(clock.withZone(users.getById(userId).zoneId()));
        LocalDate from = today.minusDays(span - 1L);
        Map<LocalDate, DailyActivity> rows = daily.findByIdUserIdAndIdDayBetweenOrderByIdDay(userId, from, today)
                .stream().collect(Collectors.toMap(DailyActivity::getDay, Function.identity()));
        return new DailySeries(from.datesUntil(today.plusDays(1))
                .map(d -> rows.containsKey(d) ? Day.of(rows.get(d)) : Day.empty(d))
                .toList());
    }

    @Transactional(readOnly = true)
    public ActivityFeed activity(UUID userId, int limit) {
        return new ActivityFeed(events.findByUserIdAndTypeNotInOrderByOccurredAtDesc(userId, FEED_EXCLUDED,
                        Limit.of(Math.clamp(limit, 1, 50))).stream()
                .map(e -> new ActivityItem(e.getType(), e.getDocumentId(), e.getDocumentTitle(), e.getTopicTitle(),
                        e.getXp(), e.getOccurredAt()))
                .toList());
    }
}
