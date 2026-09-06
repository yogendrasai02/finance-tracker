package com.financetracker.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.financetracker.common.error.ApiProblems;
import com.financetracker.common.error.ProblemResponseWriter;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * Answers requests the filter chain rejects before they reach a controller, so {@code GlobalExceptionHandler} never sees them.
 *
 * This is an API, so a rejected request gets 401 or 403 with a JSON body.
 * The default behaviour is a redirect to a login page, which a fetch call cannot act on and which turns "your session expired" into "the server returned an HTML page".
 *
 * One class covers both jobs because they are the same job: write a problem body and stop.
 */
@Component
public class SecurityErrorHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final ProblemResponseWriter problemResponseWriter;

    public SecurityErrorHandler(ProblemResponseWriter problemResponseWriter) {
        this.problemResponseWriter = problemResponseWriter;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException)
            throws IOException {
        problemResponseWriter.write(response, ApiProblems.unauthenticated());
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        problemResponseWriter.write(response, ApiProblems.accessDenied());
    }
}
