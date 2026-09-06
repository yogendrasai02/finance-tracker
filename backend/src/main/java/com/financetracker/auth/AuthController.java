package com.financetracker.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;

import com.financetracker.common.security.SessionAuthenticator;
import com.financetracker.common.tenant.CurrentTenantContext;
import com.financetracker.user.UserProfile;
import com.financetracker.user.UserService;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Login and the identity check.
 *
 * Logout is not here: it is handled by the filter chain's own logout filter, which invalidates the session server-side and clears the security context (SR-38).
 * Doing it in a controller would mean repeating that by hand.
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final SessionAuthenticator sessionAuthenticator;

    private final UserService userService;

    public AuthController(SessionAuthenticator sessionAuthenticator, UserService userService) {
        this.sessionAuthenticator = sessionAuthenticator;
        this.userService = userService;
    }

    /**
     * Returns the same body as {@code GET /api/v1/me}, so the frontend needs no second call after logging in.
     * The profile read runs under the tenant of the user who just logged in; the session-based filter cannot do it, because the session only exists from the line above.
     */
    @PostMapping("/auth/login")
    public UserProfile login(
            @Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

        long userId = sessionAuthenticator.authenticate(
                request.email(), request.password(), httpRequest, httpResponse);

        return CurrentTenantContext.runAs(userId, () -> userService.recordSuccessfulLogin(userId));
    }

    /** The tenant is already in scope here, applied by the request filter from the session. */
    @GetMapping("/me")
    public UserProfile me(@AuthenticationPrincipal AuthenticatedUser principal) {
        return userService.getProfile(principal.getUserId());
    }
}
