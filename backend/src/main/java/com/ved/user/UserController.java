package com.ved.user;

import java.util.UUID;

import com.ved.security.CurrentUserId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
class UserController {

    private final UserService userService;

    UserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping("/me")
    UserResponse me(@CurrentUserId UUID userId) {
        return UserResponse.from(userService.getById(userId));
    }
}
