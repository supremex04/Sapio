package com.ved.user;

import java.util.UUID;

import com.ved.common.error.ApplicationException;
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
}
