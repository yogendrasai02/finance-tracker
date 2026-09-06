package com.financetracker.auth;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * The owner's credential, supplied by the environment as {@code FT_OWNER_EMAIL} and {@code FT_OWNER_PASSWORD}.
 *
 * SR-40 forbids a hash in a migration, so this is where the first credential comes from.
 * The production profile binds both to environment variables with no default, so a deployment without them fails to start rather than running with an account nobody can use.
 */
@ConfigurationProperties(prefix = "ft.owner")
record OwnerCredentialProperties(String email, String password) {
}
