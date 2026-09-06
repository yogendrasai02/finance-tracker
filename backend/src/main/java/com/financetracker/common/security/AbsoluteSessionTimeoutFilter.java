package com.financetracker.common.security;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Enforces the second, independent limit SR-38 asks for: a session dies this long after it was created, even while it stays active.
 * Spring Session's own timeout is idle-only, and a cookie's own expiry is client-side and not trustworthy, so this is the one place the absolute limit is actually checked.
 *
 * Spring Session stores each session's original creation time, so no extra column is needed: it survives a restart and a reload from the database unchanged.
 * Invalidating here, before the security filters that follow, means an expired session is never handed an Authentication to restore in the first place.
 * Invalidation also deletes the row, which is what makes the session actually gone rather than merely ignored.
 */
@Component
public class AbsoluteSessionTimeoutFilter extends OncePerRequestFilter {

    private final Duration maxAge;

    AbsoluteSessionTimeoutFilter(SessionProperties sessionProperties) {
        this.maxAge = sessionProperties.absoluteTimeout();
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        HttpSession session = request.getSession(false);
        if (session != null && hasExceededMaxAge(session)) {
            session.invalidate();
        }
        filterChain.doFilter(request, response);
    }

    private boolean hasExceededMaxAge(HttpSession session) {
        Instant createdAt = Instant.ofEpochMilli(session.getCreationTime());
        return createdAt.plus(maxAge).isBefore(Instant.now());
    }
}
