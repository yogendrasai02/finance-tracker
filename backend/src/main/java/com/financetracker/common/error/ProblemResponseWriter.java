package com.financetracker.common.error;

import java.io.IOException;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.stereotype.Component;

import tools.jackson.databind.ObjectMapper;

/**
 * Writes a {@link ProblemDetail} straight to a servlet response.
 *
 * A {@code @RestControllerAdvice} method can just return a {@code ProblemDetail} and let Spring MVC serialise it.
 * Code that runs before a controller can be reached — a security filter, a rate limiter — has no such handoff and has to write the response itself.
 * This is the one place that does it, so both call sites stay a single line each.
 */
@Component
public class ProblemResponseWriter {

    private final ObjectMapper objectMapper;

    public ProblemResponseWriter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public void write(HttpServletResponse response, ProblemDetail problem) throws IOException {
        response.setStatus(problem.getStatus());
        response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(response.getOutputStream(), problem);
    }
}
