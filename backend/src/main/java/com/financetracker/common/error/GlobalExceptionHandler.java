package com.financetracker.common.error;

import lombok.extern.slf4j.Slf4j;

import org.springframework.http.ProblemDetail;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * One place that turns an exception into a response body, so no controller has to write a try-catch (BACKEND_CONVENTIONS 5.4).
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

    /**
     * "Missing" and "not yours" are the same exception on purpose (SR-04, SR-78), so this handler cannot distinguish them even if it wanted to.
     * The message is logged for whoever is debugging; it never reaches the response.
     */
    @ExceptionHandler(NotFoundException.class)
    public ProblemDetail handleNotFound(NotFoundException exception) {
        log.info("Not found: {}", exception.getMessage());
        return ApiProblems.notFound();
    }

    /** Field names and constraint messages only. The rejected value itself never leaves this method (SECURITY.md §2). */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidationFailure(MethodArgumentNotValidException exception) {
        return ApiProblems.validationFailed(exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList());
    }
}
