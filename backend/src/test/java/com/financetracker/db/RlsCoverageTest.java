package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Guards against a future migration silently adding a table with no Row-Level Security (DM-30, DM-38). */
class RlsCoverageTest extends SchemaTestBase {

    @Test
    void everyAppTableHasRowLevelSecurityEnabledAndATenantIsolationPolicy() throws SQLException {
        try (Connection connection = migratorConnection()) {
            for (String table : appSchemaTables(connection)) {
                assertThat(rowSecurityEnabled(connection, table))
                        .as("app.%s has ROW LEVEL SECURITY enabled", table)
                        .isTrue();
                assertThat(hasTenantIsolationPolicy(connection, table))
                        .as("app.%s has a tenant_isolation policy", table)
                        .isTrue();
            }
        }
    }

    private static List<String> appSchemaTables(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT table_name FROM information_schema.tables
                WHERE table_schema = 'app' AND table_type = 'BASE TABLE'
                  AND table_name <> 'flyway_schema_history'
                ORDER BY table_name
                """);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                tables.add(rows.getString("table_name"));
            }
        }
        assertThat(tables).as("app schema has tables to check").isNotEmpty();
        return tables;
    }

    private static boolean rowSecurityEnabled(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT relrowsecurity FROM pg_class WHERE relnamespace = 'app'::regnamespace AND relname = ?")) {
            statement.setString(1, table);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean("relrowsecurity");
            }
        }
    }

    private static boolean hasTenantIsolationPolicy(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname = 'app' AND tablename = ? AND policyname = 'tenant_isolation')")) {
            statement.setString(1, table);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        }
    }
}
