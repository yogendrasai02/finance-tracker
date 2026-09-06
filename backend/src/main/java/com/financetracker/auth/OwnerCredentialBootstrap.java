package com.financetracker.auth;

import java.util.Locale;
import java.util.Optional;

import com.financetracker.common.tenant.CurrentTenantContext;
import com.financetracker.user.UserService;

import lombok.extern.slf4j.Slf4j;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Gives the seeded owner a password on startup, taken from the environment.
 *
 * A migration cannot do this: migrations are committed to a public repository, and a hash in one is a hash everybody has (SR-40).
 *
 * Nothing here logs the email or the password, at any level.
 * The lookup runs as a system transaction because no tenant exists before login; the write runs as the user being changed, because Row-Level Security would otherwise hide the row from the update.
 */
@Component
@Slf4j
public class OwnerCredentialBootstrap implements ApplicationRunner {

    /** Long enough that a password typed once during development cannot quietly become the deployed one. */
    private static final int MINIMUM_PASSWORD_LENGTH = 12;

    private final OwnerCredentialProperties properties;

    private final LoginIdentityRepository loginIdentityRepository;

    private final UserService userService;

    private final PasswordEncoder passwordEncoder;

    public OwnerCredentialBootstrap(
            OwnerCredentialProperties properties,
            LoginIdentityRepository loginIdentityRepository,
            UserService userService,
            PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.loginIdentityRepository = loginIdentityRepository;
        this.userService = userService;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.warn("No owner credential configured, so nobody can log in. Set FT_OWNER_EMAIL and FT_OWNER_PASSWORD");
            return;
        }

        bootstrap(properties.email(), properties.password());
    }

    /**
     * Sets the password only when the account has none, so a restart never overwrites a credential that is already in use.
     * This is a bootstrap, not a password reset.
     */
    public void bootstrap(String email, String rawPassword) {
        if (rawPassword == null || rawPassword.length() < MINIMUM_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "The owner password must be at least " + MINIMUM_PASSWORD_LENGTH + " characters");
        }

        String normalisedEmail = email.trim().toLowerCase(Locale.ROOT);

        // IMP: identity lookup must be "runAsSystem" as not tenant exists so far (means RLS hides every user row)
        // This goes through the SECURITY DEFINER function
        Optional<LoginIdentity> identity =
                CurrentTenantContext.runAsSystem(() -> loginIdentityRepository.findByEmail(normalisedEmail));

        if (identity.isEmpty()) {
            log.warn("No user matches the configured owner email, so no credential was set");
            return;
        }

        if (StringUtils.hasText(identity.get().getPasswordHash())) {
            log.info("Owner credential already present, leaving it unchanged");
            return;
        }

        long userId = identity.get().getId();
        String encodedPassword = passwordEncoder.encode(rawPassword);

        // IMP: write action must be "runAs(userId)" and not a system transaction
        CurrentTenantContext.runAs(userId, () -> {
            userService.setPasswordHash(userId, encodedPassword);
            return null;
        });

        log.info("Owner credential initialised");
    }
}
