package com.financetracker.common.error;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One place that turns an exception into a response body, so no controller has to write a try-catch (BACKEND_CONVENTIONS 5.4).
 *
 * It only handles authentication failures so far.
 * The rest of the API's error shapes — not-found, validation, constraint violations — are added as those endpoints appear.
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    /**
     * Every subtype gets the same body: a wrong password, an unknown email, an account with no credential, and a disabled account are one answer to the client (SR-39).
     * The exception class goes to the log so the real reason is still recoverable; the message does not, because it can carry the submitted email.
     */
    @ExceptionHandler(AuthenticationException.class)
    public ProblemDetail handleAuthenticationFailure(AuthenticationException exception) {
        log.info("Login rejected: {}", exception.getClass().getSimpleName());
        return ApiProblems.invalidCredentials();
    }
}
