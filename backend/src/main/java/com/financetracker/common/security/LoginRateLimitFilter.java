package com.financetracker.common.security;

import java.io.IOException;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.financetracker.common.error.ApiProblems;
import com.financetracker.common.error.ProblemResponseWriter;

import io.github.bucket4j.ConsumptionProbe;

import org.springframework.http.HttpMethod;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Stops credential stuffing against the login endpoint before a single password comparison runs (SR-39, D-40).
 *
 * Keyed by client IP and the email in the request body together, not by either alone.
 * Keying by email alone would let anyone lock a real user out just by submitting wrong passwords for their address from anywhere.
 * Keying by IP alone would let one attacker spread guesses across many accounts from one machine at full speed.
 * The combination throttles exactly the thing SR-39 is about: repeated wrong guesses against one account from one place.
 *
 * The client IP comes from the request itself, never from a client-supplied header such as {@code X-Forwarded-For}.
 * This backend has no reverse proxy in front of it.
 * Trusting a header the caller controls would let the same caller rotate their reported address and defeat the limit entirely.
 */
@Component
class LoginRateLimitFilter extends OncePerRequestFilter {

    private final LoginRateLimiter rateLimiter;

    private final ProblemResponseWriter problemResponseWriter;

    private final ObjectMapper objectMapper;

    LoginRateLimitFilter(
            LoginRateLimiter rateLimiter, ProblemResponseWriter problemResponseWriter, ObjectMapper objectMapper) {
        this.rateLimiter = rateLimiter;
        this.problemResponseWriter = problemResponseWriter;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !(HttpMethod.POST.matches(request.getMethod())
                && SecurityConfiguration.LOGIN_PATH.equals(request.getRequestURI()));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        CachedBodyHttpServletRequest cachedRequest = new CachedBodyHttpServletRequest(request);
        String key = request.getRemoteAddr() + "|" + emailFrom(cachedRequest.body());

        ConsumptionProbe probe = rateLimiter.tryConsume(key);
        if (!probe.isConsumed()) {
            logger.info("Login rate limit exceeded for client " + request.getRemoteAddr());
            long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
            response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
            problemResponseWriter.write(response, ApiProblems.tooManyLoginAttempts());
            return;
        }

        filterChain.doFilter(cachedRequest, response);
    }

    /** Best-effort only: a malformed body still reaches the controller, which is what produces the real validation error. */
    private String emailFrom(byte[] body) {
        try {
            JsonNode node = objectMapper.readTree(body);
            return node.path("email").asString("").trim().toLowerCase(Locale.ROOT);
        } catch (RuntimeException malformed) {
            return "";
        }
    }
}
