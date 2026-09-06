package com.financetracker.auth;

import java.util.Map;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.DelegatingPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Argon2id is the current OWASP first choice: its memory cost makes cracking a stolen hash expensive on hardware that makes CPU-only work cheap (SR-40).
 *
 * The encoder is a {@link DelegatingPasswordEncoder}, so the stored hash carries its own {@code {algorithm}} prefix.
 * That is what lets the algorithm change later without a migration: new hashes get the new prefix, old ones still verify, and each user is re-hashed on their next successful login.
 * bcrypt is registered as a verifier only, for exactly the purpose described in the above line.
 */
@Configuration(proxyBeanMethods = false)
public class PasswordEncoderConfig {

    private static final String ARGON2 = "argon2";

    private static final String BCRYPT = "bcrypt";

    /** OWASP's baseline for Argon2id: 19 MiB of memory, two iterations, one lane, with a 16-byte salt and a 32-byte hash. */
    private static final int SALT_LENGTH_BYTES = 16;

    private static final int HASH_LENGTH_BYTES = 32;

    private static final int PARALLELISM = 1;

    private static final int MEMORY_KIB = 19 * 1024;

    private static final int ITERATIONS = 2;

    @Bean
    PasswordEncoder passwordEncoder() {
        Map<String, PasswordEncoder> encoders = Map.of(
                ARGON2,
                new Argon2PasswordEncoder(SALT_LENGTH_BYTES, HASH_LENGTH_BYTES, PARALLELISM, MEMORY_KIB, ITERATIONS),
                BCRYPT,
                new BCryptPasswordEncoder());

        return new DelegatingPasswordEncoder(ARGON2, encoders);
    }
}
