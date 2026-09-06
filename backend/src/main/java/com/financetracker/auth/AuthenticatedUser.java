package com.financetracker.auth;

import java.io.Serial;
import java.util.Collection;
import java.util.List;

import com.financetracker.common.tenant.TenantPrincipal;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Who is logged in, as Spring Security sees it, plus the user id the tenant machinery needs.
 *
 * This object is written into the session, and the session becomes a row in Postgres once the session store moves there, so two properties matter.
 * It has to serialize, which is why it holds only primitives and strings.
 * And the password hash has to leave it once the credential check has passed, which is what {@link #eraseCredentials()} is for: {@code ProviderManager} calls it after a successful login, so the stored session never carries a second copy of the credential.
 */
public final class AuthenticatedUser implements UserDetails, CredentialsContainer, TenantPrincipal {

    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * One user, no roles (FR-8), so this authority exists only because an authenticated principal with none is an odd shape to leave behind for later.
     * Authorization in this application is tenant isolation, not role checks.
     */
    private static final List<GrantedAuthority> AUTHORITIES = List.of(new SimpleGrantedAuthority("ROLE_USER"));

    private final long userId;

    private final String email;

    private final boolean enabled;

    private String passwordHash;

    AuthenticatedUser(long userId, String email, String passwordHash, boolean enabled) {
        this.userId = userId;
        this.email = email;
        this.passwordHash = passwordHash;
        this.enabled = enabled;
    }

    @Override
    public long getUserId() {
        return userId;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return AUTHORITIES;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        this.passwordHash = null;
    }

    /** The id only. An email address is Confidential data and must never reach a log line (SR-25, SR-26). */
    @Override
    public String toString() {
        return "AuthenticatedUser[userId=" + userId + "]";
    }
}
