package com.financetracker.common.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;

/**
 * The one place error bodies are built, so responses that must be indistinguishable actually are.
 *
 * A wrong password and an unknown email have to produce the same bytes (SR-39), and later so do "this row does not exist" and "this row is not yours" (SR-04, SR-78).
 * Two call sites building "the same" body by hand drift apart on the first edit; one factory cannot.
 */
public final class ApiProblems {

    private ApiProblems() {
    }

    /** A request with no usable session. */
    public static ProblemDetail unauthenticated() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Authentication is required");
    }

    /** Every login failure, whatever the real reason: unknown email, wrong password, no credential set, disabled account. */
    public static ProblemDetail invalidCredentials() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Invalid email or password");
    }

    /**
     * An authenticated request the filter chain refused, which today means a missing or stale CSRF token.
     * Naming the usual cause costs nothing: an attacker already knows why a forged request failed, and a developer would otherwise be guessing.
     */
    public static ProblemDetail accessDenied() {
        // A missing or stale CSRF token is the usual cause
        return ProblemDetail.forStatusAndDetail(
                HttpStatus.FORBIDDEN, "Access denied: The request was rejected.");
    }
}
