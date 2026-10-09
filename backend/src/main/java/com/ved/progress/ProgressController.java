package com.ved.progress;

import java.util.UUID;

import com.ved.progress.ProgressResponses.ActivityFeed;
import com.ved.progress.ProgressResponses.DailySeries;
import com.ved.progress.ProgressResponses.GoalRequest;
import com.ved.progress.ProgressResponses.GoalResponse;
import com.ved.progress.ProgressResponses.HeartbeatRequest;
import com.ved.progress.ProgressResponses.Summary;
import com.ved.security.CurrentUserId;
import com.ved.user.UserService;
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
