package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import org.junit.jupiter.api.Test;

/** Proves ft_app cannot switch off any control this schema relies on (DM-28, SR-48). */
class RolePrivilegeTest extends SchemaTestBase {

    private static final String INSUFFICIENT_PRIVILEGE = "42501";

    @Test
    void cannotCreateTableInAppSchema() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "CREATE TABLE app.something (id INT)");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotDropTable() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "DROP TABLE app.transactions");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotDisableTriggers() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "ALTER TABLE app.transactions DISABLE TRIGGER ALL");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotDisableRowLevelSecurity() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "ALTER TABLE app.transactions DISABLE ROW LEVEL SECURITY");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotWeakenForcedRowLevelSecurity() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "ALTER TABLE app.transactions NO FORCE ROW LEVEL SECURITY");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotDropPolicy() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "DROP POLICY tenant_isolation ON app.transactions");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotCreatePolicy() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(
                    connection,
                    "CREATE POLICY should_be_refused ON app.transactions FOR ALL USING (true)");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotChangeTableOwner() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "ALTER TABLE app.transactions OWNER TO ft_app");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotSetRoleToTheMigrator() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "SET ROLE ft_migrator");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotTruncate() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "TRUNCATE app.transactions");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void cannotCreateAnythingInThePublicSchema() throws SQLException {
        try (Connection connection = appConnection()) {
            SQLException refused = expectFailure(connection, "CREATE TABLE public.something (id INT)");
            assertThat(refused.getSQLState()).isEqualTo(INSUFFICIENT_PRIVILEGE);
        }
    }

    @Test
    void ftAppCannotBypassRowLevelSecurityOrBeASuperuser() throws SQLException {
        try (Connection connection = appConnection();
                PreparedStatement statement = connection.prepareStatement(
                        "SELECT rolbypassrls, rolsuper FROM pg_roles WHERE rolname = 'ft_app'");
                ResultSet row = statement.executeQuery()) {
            assertThat(row.next()).as("ft_app role exists").isTrue();
            assertThat(row.getBoolean("rolbypassrls")).as("rolbypassrls").isFalse();
            assertThat(row.getBoolean("rolsuper")).as("rolsuper").isFalse();
        }
    }
}
