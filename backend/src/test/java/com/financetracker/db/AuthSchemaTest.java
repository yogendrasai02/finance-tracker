package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.ResultSetMetaData;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Proves the credential columns hold their rules and that app.find_login_identity is a narrow, working exception to tenant isolation (D-35, SR-40). */
class AuthSchemaTest extends SchemaTestBase {

    private static final String SEEDED_EMAIL = "owner@financetracker.local";
    private static final String CHECK_VIOLATION = "23514";

    @Test
    void shouldBeTheOnlyWayAppRoleReadsAUserWhenThereIsNoTenantContext() throws SQLException {
        Connection app = appConnection();

        assertThat(scalar(app, "SELECT count(*) FROM app.users"))
                .as("a direct read of app.users returns nothing without a tenant id")
                .isZero();

        List<LoginIdentity> found = findLoginIdentity(app, SEEDED_EMAIL);
        assertThat(found).hasSize(1);
        assertThat(found.getFirst().id()).isPositive();
        assertThat(found.getFirst().status()).isEqualTo("ACTIVE");
    }

    @Test
    void shouldReturnNoRowsWhenTheEmailIsUnknown() throws SQLException {
        assertThat(findLoginIdentity(appConnection(), "nobody@example.invalid")).isEmpty();
    }

    @Test
    void shouldExposeOnlyTheColumnsLoginNeeds() throws SQLException {
        try (PreparedStatement statement =
                        appConnection().prepareStatement("SELECT * FROM app.find_login_identity(?)")) {
            statement.setString(1, SEEDED_EMAIL);
            try (ResultSet rows = statement.executeQuery()) {
                ResultSetMetaData columns = rows.getMetaData();
                List<String> names = new ArrayList<>();
                for (int i = 1; i <= columns.getColumnCount(); i++) {
                    names.add(columns.getColumnName(i));
                }
                assertThat(names).containsExactly("id", "password_hash", "status");
            }
        }
    }

    /**
     * The function reads a user row because its owner owns the table, and a table owner skips its own policies (DM-32).
     * FORCE ROW LEVEL SECURITY would remove that bypass, and login would then find no user with no error anywhere.
     */
    @Test
    void shouldLeaveUsersWithoutForcedRowLevelSecurityBecauseTheLookupDependsOnTheOwnerBypass() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            assertThat(scalar(
                            migrator,
                            "SELECT count(*) FROM pg_class WHERE relnamespace = 'app'::regnamespace AND relname = 'users' AND relforcerowsecurity"))
                    .isZero();
        }
    }

    @Test
    void shouldSeedTheOwnerWithNoCredential() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            assertThat(scalar(migrator, "SELECT count(*) FROM app.users WHERE email = ? AND password_hash IS NULL", SEEDED_EMAIL))
                    .as("no migration writes a password hash")
                    .isOne();
            assertThat(scalar(migrator, "SELECT count(*) FROM app.users WHERE email = ? AND status = 'ACTIVE'", SEEDED_EMAIL))
                    .isOne();
        }
    }

    @Test
    void shouldRefuseAPasswordHashWithNoAlgorithmPrefix() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            long userId = TestFixtures.insertUser(migrator);

            SQLException refused = expectFailure(
                    migrator, "UPDATE app.users SET password_hash = 'plaintext' WHERE id = ?", userId);

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_users_password_hash_encoded");
        }
    }

    @Test
    void shouldAcceptAPasswordHashCarryingItsAlgorithmPrefix() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            long userId = TestFixtures.insertUser(migrator);

            execute(
                    migrator,
                    "UPDATE app.users SET password_hash = ?, password_updated_at = now() WHERE id = ?",
                    "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c2FsdA$aGFzaA",
                    userId);

            assertThat(scalar(migrator, "SELECT count(*) FROM app.users WHERE id = ? AND password_hash IS NOT NULL", userId))
                    .isOne();
        }
    }

    @Test
    void shouldRefuseAnUnknownStatus() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            long userId = TestFixtures.insertUser(migrator);

            SQLException refused =
                    expectFailure(migrator, "UPDATE app.users SET status = 'SUSPENDED' WHERE id = ?", userId);

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_users_status");
        }
    }

    @Test
    void shouldRefuseAnEmailThatIsNotLowercase() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            SQLException refused = expectFailure(
                    migrator, "INSERT INTO app.users (email) VALUES (?)", "Mixed.Case@example.invalid");

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_users_email_lowercase");
        }
    }

    private static List<LoginIdentity> findLoginIdentity(Connection connection, String email) throws SQLException {
        List<LoginIdentity> identities = new ArrayList<>();
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT id, password_hash, status FROM app.find_login_identity(?)")) {
            statement.setString(1, email);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    identities.add(new LoginIdentity(
                            rows.getLong("id"), rows.getString("password_hash"), rows.getString("status")));
                }
            }
        }
        return identities;
    }

    private static long scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    private record LoginIdentity(long id, String passwordHash, String status) {
    }
}
