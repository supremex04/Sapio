package com.sapio.progress;

import java.util.UUID;

import com.sapio.progress.ProgressResponses.ActivityFeed;
import com.sapio.progress.ProgressResponses.DailySeries;
import com.sapio.progress.ProgressResponses.GoalRequest;
import com.sapio.progress.ProgressResponses.GoalResponse;
import com.sapio.progress.ProgressResponses.HeartbeatRequest;
import com.sapio.progress.ProgressResponses.Summary;
import com.sapio.security.CurrentUserId;
import com.sapio.user.UserService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/progress")
class ProgressController {

    private final ProgressService progress;
    private final ActivityService activity;
    private final UserService users;

    ProgressController(ProgressService progress, ActivityService activity, UserService users) {
        this.progress = progress;
        this.activity = activity;
        this.users = users;
    }

    @PostMapping("/heartbeat")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void heartbeat(@CurrentUserId UUID userId, @RequestBody(required = false) HeartbeatRequest body) {
        activity.heartbeat(userId, body == null ? null : body.timeZone());
    }

    @GetMapping("/summary")
    Summary summary(@CurrentUserId UUID userId) {
        return progress.summary(userId);
    }

    @GetMapping("/daily")
    DailySeries daily(@CurrentUserId UUID userId, @RequestParam(defaultValue = "14") int days) {
        return progress.daily(userId, days);
    }

    @GetMapping("/activity")
    ActivityFeed activity(@CurrentUserId UUID userId, @RequestParam(defaultValue = "20") int limit) {
        return progress.activity(userId, limit);
    }

    @PutMapping("/goal")
    GoalResponse goal(@CurrentUserId UUID userId, @RequestBody GoalRequest body) {
        return new GoalResponse(users.updateDailyGoal(userId, body.dailyMinutes()).getDailyGoalMinutes());
    }
}
