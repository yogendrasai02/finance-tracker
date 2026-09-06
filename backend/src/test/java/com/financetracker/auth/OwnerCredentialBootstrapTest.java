package com.financetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

import com.financetracker.db.PostgresTestContainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The bootstrap runs against a user created by this test, never against the seeded owner.
 * {@code AuthSchemaTest} asserts that the seeded owner still has no hash, which is the guard that no migration writes one.
 */
@SpringBootTest
@ExtendWith(OutputCaptureExtension.class)
class OwnerCredentialBootstrapTest {

    private static final String RAW_PASSWORD = "not-a-real-password-1234";

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresTestContainer.APP_USER);
        registry.add("spring.datasource.password", () -> PostgresTestContainer.APP_PASSWORD);
        registry.add("spring.flyway.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.flyway.user", () -> PostgresTestContainer.MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> PostgresTestContainer.MIGRATOR_PASSWORD);
    }

    @Autowired
    private OwnerCredentialBootstrap bootstrap;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private String email;

    @BeforeEach
    void createUserWithoutACredential() throws SQLException {
        email = "bootstrap-" + System.nanoTime() + "@example.invalid";
        try (Connection migrator = migratorConnection();
                PreparedStatement statement =
                        migrator.prepareStatement("INSERT INTO app.users (email, display_name) VALUES (?, 'Bootstrap')")) {
            statement.setString(1, email);
            statement.execute();
        }
    }

    @Test
    void shouldStoreAnArgon2HashThatVerifiesAgainstTheGivenPassword() throws SQLException {
        bootstrap.bootstrap(email, RAW_PASSWORD);

        String storedHash = storedHash();
        assertThat(storedHash).startsWith("{argon2}");
        assertThat(passwordEncoder.matches(RAW_PASSWORD, storedHash)).isTrue();
        assertThat(storedHash).doesNotContain(RAW_PASSWORD);
    }

    @Test
    void shouldStampPasswordUpdatedAtWhenTheCredentialIsSet() throws SQLException {
        bootstrap.bootstrap(email, RAW_PASSWORD);

        assertThat(scalar("SELECT count(*) FROM app.users WHERE email = ? AND password_updated_at IS NOT NULL"))
                .isOne();
    }

    /** A restart must never overwrite a credential that is already in use, so this is a bootstrap and not a password reset. */
    @Test
    void shouldLeaveAnExistingCredentialUnchanged() throws SQLException {
        bootstrap.bootstrap(email, RAW_PASSWORD);
        String firstHash = storedHash();

        bootstrap.bootstrap(email, "a-completely-different-password");

        assertThat(storedHash()).isEqualTo(firstHash);
    }

    @Test
    void shouldMatchTheEmailWhateverCaseItIsConfiguredIn() throws SQLException {
        bootstrap.bootstrap(email.toUpperCase(Locale.ROOT), RAW_PASSWORD);

        assertThat(storedHash()).startsWith("{argon2}");
    }

    @Test
    void shouldRefuseAPasswordShorterThanTheMinimum() {
        assertThatThrownBy(() -> bootstrap.bootstrap(email, "short"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("12 characters");
    }

    @Test
    void shouldDoNothingWhenNoUserMatchesTheConfiguredEmail() throws SQLException {
        bootstrap.bootstrap("nobody@example.invalid", RAW_PASSWORD);

        assertThat(scalar("SELECT count(*) FROM app.users WHERE email = ? AND password_hash IS NOT NULL")).isZero();
    }

    /** The first instance of the SR-29 pattern: the flow runs, its log output is captured, and none of the sensitive input appears in it. */
    @Test
    void shouldKeepTheEmailAndPasswordOutOfTheLogs(CapturedOutput output) {
        bootstrap.bootstrap(email, RAW_PASSWORD);

        assertThat(output).as("the flow logged something, so this assertion is looking at real output")
                .contains("Owner credential initialised");
        assertThat(output).doesNotContain(RAW_PASSWORD);
        assertThat(output).doesNotContain(email);
    }

    private String storedHash() throws SQLException {
        try (Connection migrator = migratorConnection();
                PreparedStatement statement =
                        migrator.prepareStatement("SELECT password_hash FROM app.users WHERE email = ?")) {
            statement.setString(1, email);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getString(1);
            }
        }
    }

    private long scalar(String sql) throws SQLException {
        try (Connection migrator = migratorConnection();
                PreparedStatement statement = migrator.prepareStatement(sql)) {
            statement.setString(1, email);
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }

    private static Connection migratorConnection() throws SQLException {
        return DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(),
                PostgresTestContainer.MIGRATOR_USER,
                PostgresTestContainer.MIGRATOR_PASSWORD);
    }
}
