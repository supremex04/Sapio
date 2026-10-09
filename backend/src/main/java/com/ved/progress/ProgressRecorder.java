package com.ved.progress;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.ved.auth.UserLoggedInEvent;
import com.ved.study.LearningActivityEvent;
import com.ved.study.LearningActivityEvent.Type;
import com.ved.study.question.Verdict;
import com.ved.user.UserService;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Turns domain events into analytics and XP. Runs synchronously in the publisher's transaction, so a
 * learning action and its XP are committed together (and never double-counted on rollback).
 */
@Component
class ProgressRecorder {

    private final LearningEventRepository events;
    private final DailyActivityRepository daily;
    private final UserService users;
    private final Clock clock;

    ProgressRecorder(LearningEventRepository events, DailyActivityRepository daily, UserService users, Clock clock) {
        this.events = events;
        this.daily = daily;
        this.users = users;
        this.clock = clock;
    }

    @EventListener
    void onLogin(UserLoggedInEvent event) {
        daily.add(event.userId(), localDate(event.userId(), event.occurredAt()), 0, 1, 0, 0, 0, 0);
    }

    @EventListener
    void onLearning(LearningActivityEvent e) {
        int xp = XpPolicy.xpFor(e);
        LocalDate day = localDate(e.userId(), e.occurredAt());
        events.save(new LearningEventRecord(e.userId(), e.type().name(), e.documentId(), e.documentTitle(),
                e.topicId(), e.topicTitle(), e.assessmentId(), e.questionId(),
                e.verdict() == null ? null : e.verdict().name(),
                e.confidence() == null ? null : e.confidence().name(), xp, day, e.occurredAt()));
        boolean answered = e.type() == Type.CHECK_ANSWERED || e.type() == Type.ASSESSMENT_ANSWERED
                || e.type() == Type.REVIEW_ANSWERED;
        daily.add(e.userId(), day, 0, 0, e.type() == Type.TOPIC_COMPLETED ? 1 : 0, answered ? 1 : 0,
                answered && e.verdict() == Verdict.CORRECT ? 1 : 0, xp);
    }

    /** Records reaching the daily goal (called by {@link ActivityService}). */
    void goalReached(UUID userId, LocalDate day) {
        events.save(new LearningEventRecord(userId, LearningEventRecord.GOAL_REACHED, null, null, null, null, null,
                null, null, null, XpPolicy.DAILY_GOAL_REACHED, day, clock.instant()));
        daily.add(userId, day, 0, 0, 0, 0, 0, XpPolicy.DAILY_GOAL_REACHED);
    }

    private LocalDate localDate(UUID userId, Instant at) {
        return LocalDate.ofInstant(at, users.getById(userId).zoneId());
    }
}
