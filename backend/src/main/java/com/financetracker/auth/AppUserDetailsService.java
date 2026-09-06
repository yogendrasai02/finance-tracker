package com.financetracker.auth;

import java.util.Locale;

import com.financetracker.common.tenant.CurrentTenantContext;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Loads what the credential check needs, through the one privileged read in the application (D-35, DM-40).
 *
 * This class does not verify the password.
 * The comparison, and the dummy-hash comparison that makes an unknown email cost the same as a wrong password, are both in {@code DaoAuthenticationProvider}, wired in {@code SecurityConfiguration}.
 *
 * The lookup runs as a system transaction because login has no tenant yet.
 * Every other read of {@code app.users} stays hidden by Row-Level Security.
 *
 * The messages below never reach the client, and are not what makes failures uniform (SR-39).
 * {@code DaoAuthenticationProvider} discards them, and {@code GlobalExceptionHandler} maps every {@code AuthenticationException} to one response body.
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

    private static final String ACTIVE = "ACTIVE";

    private final LoginIdentityRepository loginIdentityRepository;

    public AppUserDetailsService(LoginIdentityRepository loginIdentityRepository) {
        this.loginIdentityRepository = loginIdentityRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String email) {
        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);

        LoginIdentity identity = CurrentTenantContext.runAsSystem(
                        () -> loginIdentityRepository.findByEmail(normalisedEmail))
                .orElseThrow(() -> new UsernameNotFoundException("No login identity for the given email"));

        // An account waiting for its credential must look exactly like an account that does not exist.
        if (!StringUtils.hasText(identity.getPasswordHash())) {
            throw new UsernameNotFoundException("The account has no credential");
        }

        return new AuthenticatedUser(
                identity.getId(), normalisedEmail, identity.getPasswordHash(), ACTIVE.equals(identity.getStatus()));
    }
}
