package com.financetracker.common.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.financetracker.common.error.ApiProblems;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

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

    private final ObjectMapper objectMapper;

    public SecurityErrorHandler(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public void commence(
            HttpServletRequest request, HttpServletResponse response, AuthenticationException authenticationException)
            throws IOException {
        write(response, HttpStatus.UNAUTHORIZED, ApiProblems.unauthenticated());
    }

    @Override
    public void handle(
            HttpServletRequest request, HttpServletResponse response, AccessDeniedException accessDeniedException)
            throws IOException {
        write(response, HttpStatus.FORBIDDEN, ApiProblems.accessDenied());
    }

    private void write(HttpServletResponse response, HttpStatus status, ProblemDetail problem) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
