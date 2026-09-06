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
        User user = requireVisible(userId);

        user.setPasswordHash(encodedPasswordHash);
        user.setPasswordUpdatedAt(Instant.now());
    }

    /** The caller must have the tenant in scope, so a user can only ever read their own profile. */
    @Transactional(readOnly = true)
    public UserProfile getProfile(long userId) {
        return toProfile(requireVisible(userId));
    }

    /**
     * Stamps the login time and returns the profile in one transaction, because login needs both and they are the same row.
     */
    @Transactional
    public UserProfile recordSuccessfulLogin(long userId) {
        User user = requireVisible(userId);
        user.setLastLoginAt(Instant.now());
        return toProfile(user);
    }

    private User requireVisible(long userId) {
        return userRepository
                .findById(userId)
                .orElseThrow(() -> new IllegalStateException("No user with id " + userId + " is visible to this tenant"));
    }

    private static UserProfile toProfile(User user) {
        return new UserProfile(user.getId(), user.getEmail(), user.getDisplayName());
    }
}
