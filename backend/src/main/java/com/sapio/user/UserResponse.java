package com.sapio.user;

import java.time.Instant;
import java.util.UUID;

public record UserResponse(UUID id, String email, String fullName, Short age, Role role, String timeZone,
                           int dailyGoalMinutes, Instant createdAt) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getFullName(), user.getAge(), user.getRole(),
                user.getTimeZone(), user.getDailyGoalMinutes(), user.getCreatedAt());
    }
}
