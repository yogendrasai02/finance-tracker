package com.financetracker.user;

import java.time.Instant;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The user feature's public surface, so other features never reach for its repository (BACKEND_CONVENTIONS 3.1). */
@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /**
     * Stores an already-encoded password hash.
     * The caller must have the tenant in scope, because Row-Level Security hides the row otherwise.
     */
    @Transactional
    public void setPasswordHash(long userId, String encodedPasswordHash) {
        User user = userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("No user with id " + userId + " is visible to this tenant"));

        user.setPasswordHash(encodedPasswordHash);
        user.setPasswordUpdatedAt(Instant.now());
    }
}
