package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

/** Proves the DM-25 {@code updated_at} trigger fires, on every table that carries one. */
class TriggerTimestampTest extends SchemaTestBase {

    @Test
    void usersUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "users", userId, c -> execute(c, "UPDATE app.users SET display_name = 'renamed' WHERE id = ?", userId));
    }

    @Test
    void accountsUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "accounts", accountId,
                c -> execute(c, "UPDATE app.accounts SET is_active = FALSE WHERE id = ?", accountId));
    }

    @Test
    void categoriesUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "categories", categoryId,
                c -> execute(c, "UPDATE app.categories SET is_active = FALSE WHERE id = ?", categoryId));
    }

    @Test
    void categoryRulesUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");
        long categoryRuleId = TestFixtures.insertCategoryRule(migrator, userId, null, categoryId);
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "category_rules", categoryRuleId,
                c -> execute(c, "UPDATE app.category_rules SET priority = priority + 1 WHERE id = ?", categoryRuleId));
    }

    @Test
    void statementImportsUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "statement_imports", statementImportId,
                c -> execute(c, "UPDATE app.statement_imports SET source_filename = 'renamed.csv' WHERE id = ?", statementImportId));
    }

    @Test
    void statementImportRowsUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        long importRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "statement_import_rows", importRowId,
                c -> execute(c, "UPDATE app.statement_import_rows SET row_status = 'DUPLICATE' WHERE id = ?", importRowId));
    }

    @Test
    void transactionsUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long transactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "original");
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "transactions", transactionId,
                c -> execute(c, "UPDATE app.transactions SET narration = 'renamed' WHERE id = ?", transactionId));
    }

    @Test
    void balanceCheckpointsUpdatedAtMovesOnUpdate() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long checkpointId = TestFixtures.insertBalanceCheckpoint(migrator, userId, accountId);
        assertUpdateStampsUpdatedAtAndLeavesCreatedAt(
                migrator, "balance_checkpoints", checkpointId,
                c -> execute(c, "UPDATE app.balance_checkpoints SET note = 'reconciled' WHERE id = ?", checkpointId));
    }

    /**
     * Postgres's {@code now()} is the transaction's start time, fixed for every statement in it — not the statement's own clock time.
     * So the fixture insert is committed first; otherwise an update run in the same transaction as its insert would stamp an identical {@code updated_at}.
     */
    private void assertUpdateStampsUpdatedAtAndLeavesCreatedAt(Connection connection, String table, long id, SqlWork update)
            throws SQLException {
        connection.commit();

        Timestamps before = readTimestamps(connection, table, id);
        update.run(connection);
        Timestamps after = readTimestamps(connection, table, id);

        assertThat(after.createdAt())
                .as("app.%s.created_at is untouched by an update", table)
                .isEqualTo(before.createdAt());
        assertThat(after.updatedAt())
                .as("app.%s.updated_at moves forward on an update", table)
                .isAfter(before.updatedAt());
    }

    private static Timestamps readTimestamps(Connection connection, String table, long id) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT created_at, updated_at FROM app." + table + " WHERE id = ?")) {
            statement.setLong(1, id);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).as("app.%s has a row with id %d", table, id).isTrue();
                return new Timestamps(
                        row.getObject("created_at", OffsetDateTime.class), row.getObject("updated_at", OffsetDateTime.class));
            }
        }
    }

    private record Timestamps(OffsetDateTime createdAt, OffsetDateTime updatedAt) {
    }
}
