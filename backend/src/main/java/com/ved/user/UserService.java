package com.ved.user;

import java.util.UUID;

import com.ved.common.error.ApplicationException;
import com.ved.common.error.ErrorCode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository users;

    public UserService(UserRepository users) {
        this.users = users;
    }

    @Transactional(readOnly = true)
    public User getById(UUID id) {
        return users.findById(id).orElseThrow(() -> ApplicationException.notFound("User"));
    }

    @Transactional
    public void updateTimeZone(UUID id, String timeZone) {
        getById(id).updateTimeZone(timeZone);
    }

    @Transactional
    public User updateDailyGoal(UUID id, int minutes) {
        if (!User.ALLOWED_DAILY_GOALS.contains(minutes)) {
            throw new ApplicationException(ErrorCode.VALIDATION_FAILED,
                    "Daily goal must be one of " + User.ALLOWED_DAILY_GOALS.stream().sorted().toList());
        }
        User user = getById(id);
        user.setDailyGoalMinutes(minutes);
        return user;
    }
}
