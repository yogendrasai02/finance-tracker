package com.financetracker.common.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import com.financetracker.common.tenant.CurrentTenantContext;
import com.financetracker.common.tenant.TenantPrincipal;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Copies the logged-in user's id from the security context into the tenant scope for the length of one request.
 *
 * This is the link between "who is this" and "which rows may this query see".
 * It runs at the end of the security chain, after the context has been loaded and after authorization has passed, so an unauthenticated request never reaches it with a tenant.
 *
 * A request with no authenticated principal is passed through untouched.
 * It has no tenant, so any transaction it somehow starts is refused rather than silently returning nothing.
 */
@Component
public class TenantContextFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof TenantPrincipal principal)) {
            filterChain.doFilter(request, response);
            return;
        }

        try {
            CurrentTenantContext.runAs(principal.getUserId(), () -> {
                filterChain.doFilter(request, response);
                return null;
            });
        } catch (IOException | ServletException | RuntimeException | Error rethrown) {
            // The scoped runner is generic over a single exception type, and the filter chain throws two, so the compiler widens it to Exception here.
            // Nothing downstream can throw a checked exception other than those two.
            throw rethrown;
        } catch (Exception unexpected) {
            throw new IllegalStateException("Unexpected checked exception from the filter chain", unexpected);
        }
    }
}
