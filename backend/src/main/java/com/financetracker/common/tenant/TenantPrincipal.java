package com.financetracker.common.tenant;

/**
 * The one thing the tenant machinery needs from whoever is logged in: their user id.
 *
 * The authenticated principal lives in {@code com.financetracker.auth}, and {@code common} must not depend on a feature package.
 * This interface is the seam: {@code auth} implements it, and the request filter reads the id through it without knowing the principal type.
 */
public interface TenantPrincipal {

    long getUserId();
}
