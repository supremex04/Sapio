package com.ved.progress;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import com.ved.user.User;
import com.ved.user.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Active-time tracking from client heartbeats (sent every ~30 s while the learner is active).
 * The server credits min(gap, 30 s) when the previous beat was ≤ 120 s ago, so time can't be inflated
 * by spamming, and needs no trust in client clocks.
 */
@Service
class ActivityService {

    static final Duration MAX_CREDIT = Duration.ofSeconds(30);
    static final Duration SESSION_GAP = Duration.ofSeconds(120);

    private final UserPresenceRepository presence;
    private final DailyActivityRepository daily;
    private final ProgressRecorder recorder;
    private final UserService users;
    private final Clock clock;

    ActivityService(UserPresenceRepository presence, DailyActivityRepository daily, ProgressRecorder recorder,
                    UserService users, Clock clock) {
        this.presence = presence;
        this.daily = daily;
        this.recorder = recorder;
        this.users = users;
        this.clock = clock;
    }

    @Transactional
    public void heartbeat(UUID userId, String timeZone) {
        Instant now = clock.instant();
        users.updateTimeZone(userId, timeZone);
        if (presence.insertIfAbsent(userId, now) == 1) {
            return; // very first heartbeat: starts a session, credits nothing
        }
        UserPresence p = presence.findForUpdate(userId).orElseThrow();
        Duration gap = Duration.between(p.getLastHeartbeatAt(), now);
        p.beat(now);
        if (gap.isNegative() || gap.compareTo(SESSION_GAP) > 0) {
            return; // new session after a break
        }
        int credit = (int) Math.min(gap.toSeconds(), MAX_CREDIT.toSeconds());
        if (credit <= 0) {
            return;
        }
        User user = users.getById(userId);
        LocalDate today = LocalDate.ofInstant(now, user.zoneId());
        daily.add(userId, today, credit, 0, 0, 0, 0, 0);
        if (daily.markGoalReached(userId, today, user.getDailyGoalMinutes() * 60) == 1) {
            recorder.goalReached(userId, today);
        }
    }
}
