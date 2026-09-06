package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Proves V6's session-store tables exist in auth, not app, and are reachable by ft_app (D-34).
 * They deliberately carry no Row-Level Security, so RlsCoverageTest and SchemaConventionsTest are correct to leave them unchecked: they only ever scan app.
 */
class SessionStoreSchemaTest extends SchemaTestBase {

    @Test
    void bothSessionTablesExistInTheAuthSchema() throws SQLException {
        try (Connection connection = migratorConnection()) {
            Set<String> tables = new HashSet<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    """
                    SELECT table_name FROM information_schema.tables
                    WHERE table_schema = 'auth' AND table_type = 'BASE TABLE'
                    """);
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    tables.add(rows.getString("table_name"));
                }
            }
            assertThat(tables).containsExactlyInAnyOrder("spring_session", "spring_session_attributes");
        }
    }

    @Test
    void neitherSessionTableHasRowLevelSecurity() throws SQLException {
        try (Connection connection = migratorConnection()) {
            for (String table : new String[] {"spring_session", "spring_session_attributes"}) {
                try (PreparedStatement statement = connection.prepareStatement(
                        "SELECT relrowsecurity FROM pg_class WHERE relnamespace = 'auth'::regnamespace AND relname = ?")) {
                    statement.setString(1, table);
                    try (ResultSet row = statement.executeQuery()) {
                        assertThat(row.next()).isTrue();
                        assertThat(row.getBoolean("relrowsecurity"))
                                .as("auth.%s has no Row-Level Security: it is what establishes the tenant", table)
                                .isFalse();
                    }
                }
            }
        }
    }

    /** ft_app is the role the running application connects as, so this is the grant the session repository actually depends on. */
    @Test
    void ftAppCanReadAndWriteBothSessionTables() throws SQLException {
        try (Connection connection = appConnection()) {
            for (String table : new String[] {"spring_session", "spring_session_attributes"}) {
                try (PreparedStatement statement = connection.prepareStatement(
                        """
                        SELECT bool_and(has_table_privilege('ft_app', ?, priv))
                        FROM unnest(ARRAY['SELECT', 'INSERT', 'UPDATE', 'DELETE']) AS priv
                        """)) {
                    statement.setString(1, "auth." + table);
                    try (ResultSet row = statement.executeQuery()) {
                        row.next();
                        assertThat(row.getBoolean(1))
                                .as("ft_app has SELECT, INSERT, UPDATE and DELETE on auth.%s", table)
                                .isTrue();
                    }
                }
            }
        }
    }

    @Test
    void deletingASessionCascadesToItsAttributes() throws SQLException {
        try (Connection connection = migratorConnection()) {
            execute(
                    connection,
                    """
                    INSERT INTO auth.spring_session
                        (primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time)
                    VALUES ('11111111-1111-1111-1111-111111111111', '22222222-2222-2222-2222-222222222222', 0, 0, 1800, 0)
                    """);
            execute(
                    connection,
                    """
                    INSERT INTO auth.spring_session_attributes (session_primary_id, attribute_name, attribute_bytes)
                    VALUES ('11111111-1111-1111-1111-111111111111', 'SPRING_SECURITY_CONTEXT', '\\x00'::bytea)
                    """);

            execute(connection, "DELETE FROM auth.spring_session WHERE primary_id = ?", "11111111-1111-1111-1111-111111111111");

            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT count(*) FROM auth.spring_session_attributes WHERE session_primary_id = ?")) {
                statement.setString(1, "11111111-1111-1111-1111-111111111111");
                try (ResultSet row = statement.executeQuery()) {
                    row.next();
                    assertThat(row.getLong(1)).as("the attribute row was cascaded away with its session").isZero();
                }
            }
        }
    }
}
