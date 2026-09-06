package com.financetracker.common.error;

import java.util.List;

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

    /** The login endpoint's own limit (SR-39, D-40). Says nothing about which of the two limits was hit, or which account it was for. */
    public static ProblemDetail tooManyLoginAttempts() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.TOO_MANY_REQUESTS, "Too many attempts. Try again later.");
    }

    /** "This row does not exist" and "this row is not yours" (SR-04, SR-78): the one body both of them return. */
    public static ProblemDetail notFound() {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, "The requested resource does not exist");
    }

    /** Field names and constraint messages only. Never the rejected value: it can be financial data (SECURITY.md §2). */
    static ProblemDetail validationFailed(List<FieldViolation> violations) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation failed");
        problem.setProperty("errors", violations);
        return problem;
    }
}
