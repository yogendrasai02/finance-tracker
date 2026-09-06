package com.financetracker.common.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.financetracker.common.tenant.TenantPrincipal;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.context.SecurityContextHolderStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * Checks a password and, if it holds, starts the session. Returns the id of whoever just logged in.
 *
 * Login is a controller method in this application rather than a filter, so the four steps a login filter would perform are written out here instead of inherited.
 * Leaving any of them out produces a login that appears to work, which is exactly the failure mode security configuration is prone to.
 * A test covers each one.
 */
@Component
public class SessionAuthenticator {

    private final AuthenticationManager authenticationManager;

    private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

    private final SecurityContextRepository securityContextRepository;

    private final SecurityContextHolderStrategy securityContextHolderStrategy;

    public SessionAuthenticator(
            AuthenticationManager authenticationManager,
            SessionAuthenticationStrategy sessionAuthenticationStrategy,
            SecurityContextRepository securityContextRepository) {
        this.authenticationManager = authenticationManager;
        this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
        this.securityContextRepository = securityContextRepository;
        this.securityContextHolderStrategy = SecurityContextHolder.getContextHolderStrategy();
    }

    /**
     * Throws {@code AuthenticationException} on any failure, which the global handler turns into one uniform 401 (SR-39).
     */
    public long authenticate(
            String email, String rawPassword, HttpServletRequest request, HttpServletResponse response) {

        // check the password
        Authentication authentication =
                authenticationManager.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(email, rawPassword));

        // new session id + CSRF
        // Replaces the session id, and the CSRF token with it, so a value planted in the browser before login is worthless afterwards.
        sessionAuthenticationStrategy.onAuthentication(authentication, request, response);

        // set context for this request
        SecurityContext context = securityContextHolderStrategy.createEmptyContext();
        context.setAuthentication(authentication);
        securityContextHolderStrategy.setContext(context);

        // store context for later requests
        // The filter that loads the context on later requests does not save it, so an explicit save is what makes the login stick.
        securityContextRepository.saveContext(context, request, response);

        if (authentication.getPrincipal() instanceof TenantPrincipal principal) {
            return principal.getUserId();
        }
        throw new IllegalStateException("The authenticated principal carries no user id, so no query could be scoped");
    }
}
