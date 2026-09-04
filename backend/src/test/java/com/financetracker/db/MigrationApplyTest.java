package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

/** Proves the harness works: all four migrations apply to an empty database and produce the schema DATA_MODEL.md describes. */
class MigrationApplyTest extends SchemaTestBase {

    private static final List<String> EXPECTED_TABLES = List.of(
            "accounts",
            "balance_checkpoints",
            "categories",
            "category_rules",
            "dismissed_matches",
            "statement_import_rows",
            "statement_imports",
            "transaction_link_members",
            "transaction_links",
            "transactions",
            "users");

    @Test
    void allFourMigrationsApplied() throws SQLException {
        try (Connection connection = migratorConnection()) {
            List<String> versions = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement(
                    "SELECT version, success FROM app.flyway_schema_history ORDER BY installed_rank");
                    ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    assertThat(rows.getBoolean("success"))
                            .as("migration %s succeeded", rows.getString("version"))
                            .isTrue();
                    versions.add(rows.getString("version"));
                }
            }
            assertThat(versions).containsExactly("1", "2", "3", "4");
        }
    }

    @Test
    void everyExpectedTableExists() throws SQLException {
        try (Connection connection = migratorConnection()) {
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
            assertThat(tables).containsExactlyElementsOf(EXPECTED_TABLES);
        }
    }

    /**
     * Every insert in V4 is a SELECT ... WHERE u.email = '...'.
     * If that predicate ever stops matching, the insert adds zero rows and still succeeds, leaving a user with no accounts and no error anywhere.
     * Nothing else about the seed data is asserted: names and counts are the owner's own data, not a rule the schema has to hold.
     */
    @Test
    void seededUserHasAccountsAndCategories() throws SQLException {
        try (Connection connection = migratorConnection()) {
            long userId = scalar(connection, "SELECT min(id) FROM app.users");
            assertThat(userId).as("V4 seeded a user").isPositive();

            assertThat(scalar(connection, "SELECT count(*) FROM app.accounts WHERE user_id = ?", userId))
                    .as("seeded user has at least one account")
                    .isPositive();
            assertThat(scalar(connection, "SELECT count(*) FROM app.categories WHERE user_id = ?", userId))
                    .as("seeded user has at least one category")
                    .isPositive();
        }
    }

    private static long scalar(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet rows = statement.executeQuery()) {
                rows.next();
                return rows.getLong(1);
            }
        }
    }
}
