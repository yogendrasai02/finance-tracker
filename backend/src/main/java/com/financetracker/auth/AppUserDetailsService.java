package com.financetracker.auth;

import java.util.Locale;

import com.financetracker.common.tenant.CurrentTenantContext;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * Turns an email address into the credential check's input, through the one privileged read in the application (D-35, DM-40).
 *
 * The lookup runs as a system transaction because login has no tenant yet.
 * Every other read of {@code app.users} stays hidden by Row-Level Security.
 *
 * Every failure here is a {@link UsernameNotFoundException}, which {@code DaoAuthenticationProvider} turns into the same {@code BadCredentialsException} a wrong password produces, after running a password comparison against a dummy hash.
 * That is what makes a wrong email and a wrong password cost the same and say the same thing (SR-39).
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
