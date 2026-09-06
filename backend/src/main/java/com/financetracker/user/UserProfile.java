package com.financetracker.user;

/**
 * What the frontend is told about the person it is talking to.
 *
 * Login and {@code GET /api/v1/me} both return this, so the frontend needs no second call after logging in.
 * It carries nothing beyond identity: no credential fields, no timestamps, nothing financial.
 */
public record UserProfile(long userId, String email, String displayName) {
}
