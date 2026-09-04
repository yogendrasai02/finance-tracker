package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

/**
 * Catalog sweeps for the conventions every table in {@code app} must follow, not only the ones that happen to exist today (SPEC §2.2, DM-23, DM-25, DM-38).
 * A migration that breaks one of these fails here on its first run, with no per-table test to remember to add.
 */
class SchemaConventionsTest extends SchemaTestBase {

    private static final Set<String> NON_INTEGER_MONEY_TYPES = Set.of("real", "double precision", "numeric");

    private static final Set<String> TIMESTAMP_COLUMNS =
            Set.of("created_at", "updated_at", "committed_at", "dismissed_at");

    private static final List<String> DOCUMENTED_INDEXES = List.of(
            "ix_transactions_user_txn_date",
            "ix_transactions_account_txn_date_id",
            "ux_transactions_fingerprint",
            "ix_transactions_statement_import_id",
            "ux_statement_imports_cc_statement",
            "ux_import_rows_import_row_number",
            "ix_import_rows_related_transaction",
            "ux_dismissed_matches_pair");

    @Test
    void moneyIsIntegerPaiseNeverAFloatingOrDecimalType() throws SQLException {
        try (Connection connection = migratorConnection()) {
            for (Column column : appSchemaColumns(connection)) {
                assertThat(NON_INTEGER_MONEY_TYPES)
                        .as("app.%s.%s is not a floating-point or decimal type", column.table(), column.name())
                        .doesNotContain(column.dataType());
                if (column.name().endsWith("_paise")) {
                    assertThat(column.dataType())
                            .as("app.%s.%s ends in _paise, so it must be bigint", column.table(), column.name())
                            .isEqualTo("bigint");
                }
            }
        }
    }

    @Test
    void timestampColumnsAreAlwaysTimestamptz() throws SQLException {
        try (Connection connection = migratorConnection()) {
            for (Column column : appSchemaColumns(connection)) {
                if (TIMESTAMP_COLUMNS.contains(column.name())) {
                    assertThat(column.dataType())
                            .as("app.%s.%s is TIMESTAMPTZ, not plain timestamp", column.table(), column.name())
                            .isEqualTo("timestamp with time zone");
                }
            }
        }
    }

    @Test
    void everyDomainTableCarriesTheTenantAsANotNullUserId() throws SQLException {
        try (Connection connection = migratorConnection()) {
            for (String table : appSchemaTables(connection)) {
                if (table.equals("users")) {
                    continue;
                }
                assertThat(userIdNullability(connection, table))
                        .as("app.%s has a NOT NULL user_id column", table)
                        .isEqualTo("NO");
            }
        }
    }

    /**
     * DM-23: a foreign key to a user-owned parent is composite, so the parent needs {@code UNIQUE (user_id, id)} for that key to reference.
     * The set of tables that must carry it is derived from the composite foreign keys themselves, not listed by hand, so a new one added in a later migration is covered automatically.
     */
    @Test
    void everyParentReferencedByACompositeForeignKeyHasUniqueUserIdAndId() throws SQLException {
        try (Connection connection = migratorConnection()) {
            List<String> parents = tablesReferencedByCompositeForeignKey(connection);
            assertThat(parents).as("app schema has composite foreign keys to check").isNotEmpty();
            for (String table : parents) {
                assertThat(hasUniqueConstraintCoveringUserIdAndId(connection, table))
                        .as("app.%s has a UNIQUE (user_id, id) constraint", table)
                        .isTrue();
            }
        }
    }

    @Test
    void theIndexesDocumentedInDataModelSection9Exist() throws SQLException {
        try (Connection connection = migratorConnection()) {
            for (String indexName : DOCUMENTED_INDEXES) {
                assertThat(indexExists(connection, indexName))
                        .as("app has an index named %s", indexName)
                        .isTrue();
            }
        }
    }

    private static List<Column> appSchemaColumns(Connection connection) throws SQLException {
        List<Column> columns = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT table_name, column_name, data_type FROM information_schema.columns
                WHERE table_schema = 'app' AND table_name <> 'flyway_schema_history'
                """);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                columns.add(new Column(
                        rows.getString("table_name"), rows.getString("column_name"), rows.getString("data_type")));
            }
        }
        assertThat(columns).as("app schema has columns to check").isNotEmpty();
        return columns;
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

    private static String userIdNullability(Connection connection, String table) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT is_nullable FROM information_schema.columns
                WHERE table_schema = 'app' AND table_name = ? AND column_name = 'user_id'
                """)) {
            statement.setString(1, table);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).as("app.%s has a user_id column", table).isTrue();
                return row.getString("is_nullable");
            }
        }
    }

    private static List<String> tablesReferencedByCompositeForeignKey(Connection connection) throws SQLException {
        List<String> tables = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT DISTINCT c.relname
                FROM pg_constraint con
                JOIN pg_class c ON c.oid = con.confrelid
                WHERE con.contype = 'f'
                  AND con.connamespace = 'app'::regnamespace
                  AND cardinality(con.conkey) > 1
                ORDER BY c.relname
                """);
                ResultSet rows = statement.executeQuery()) {
            while (rows.next()) {
                tables.add(rows.getString("relname"));
            }
        }
        return tables;
    }

    private static boolean hasUniqueConstraintCoveringUserIdAndId(Connection connection, String table)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                """
                SELECT EXISTS (
                    SELECT 1
                    FROM pg_constraint con
                    WHERE con.conrelid = ('app.' || ?)::regclass
                      AND con.contype IN ('u', 'p')
                      AND (
                          SELECT array_agg(a.attname::text)
                          FROM unnest(con.conkey) AS k(attnum)
                          JOIN pg_attribute a ON a.attrelid = con.conrelid AND a.attnum = k.attnum
                      ) @> ARRAY['user_id', 'id']
                )
                """)) {
            statement.setString(1, table);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        }
    }

    private static boolean indexExists(Connection connection, String indexName) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT EXISTS (SELECT 1 FROM pg_indexes WHERE schemaname = 'app' AND indexname = ?)")) {
            statement.setString(1, indexName);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getBoolean(1);
            }
        }
    }

    private record Column(String table, String name, String dataType) {
    }
}
