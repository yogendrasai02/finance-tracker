package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

/**
 * Proves the DM-02 immutability triggers actually block edits to imported financial facts (SECURITY.md SR-70).
 * Also proves what they deliberately do not block: DM-05's interpretation columns, and MANUAL rows.
 */
class TriggerImmutabilityTest extends SchemaTestBase {

    private static final String RAISE_EXCEPTION = "P0001";

    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedTransactionColumns")
    void protectedColumnCannotBeChangedOnAnImportedTransaction(String column, Object newValue) throws SQLException {
        Connection migrator = migratorConnection();
        ImportedFixture fixture = createImportedTransaction(migrator, "fixture narration", 100_00L);

        SQLException refused = expectFailure(
                migrator, "UPDATE app.transactions SET " + column + " = ? WHERE id = ?", newValue, fixture.transactionId());

        assertThat(refused.getSQLState()).isEqualTo(RAISE_EXCEPTION);
        assertThat(refused.getMessage()).contains("cannot change " + column + " on an IMPORTED transaction");
    }

    static Stream<Arguments> protectedTransactionColumns() {
        return Stream.of(
                Arguments.of("id", 999_999_999L),
                Arguments.of("user_id", 999_999_999L),
                Arguments.of("account_id", 999_999_999L),
                Arguments.of("txn_date", LocalDate.of(2020, 1, 1)),
                Arguments.of("txn_time", LocalTime.of(10, 30)),
                Arguments.of("amount_paise", 42_00L),
                Arguments.of("narration", "changed narration"),
                Arguments.of("narration_normalized", "changed narration"),
                Arguments.of("balance_after_paise", 12_345L),
                Arguments.of("statement_import_id", 999_999_999L),
                Arguments.of("source_row_id", 999_999_999L),
                Arguments.of("source_row_fingerprint", "changed-fingerprint"),
                Arguments.of("fingerprint_version", (short) 2));
    }

    @Test
    void sourceCannotBeChangedOnAnImportedTransaction() throws SQLException {
        Connection migrator = migratorConnection();
        ImportedFixture fixture = createImportedTransaction(migrator, "fixture narration", 100_00L);

        SQLException refused =
                expectFailure(migrator, "UPDATE app.transactions SET source = 'MANUAL' WHERE id = ?", fixture.transactionId());

        assertThat(refused.getSQLState()).isEqualTo(RAISE_EXCEPTION);
        assertThat(refused.getMessage()).contains("cannot change source on a transaction");
    }

    @Test
    void sourceCannotBeChangedOnAManualTransaction() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long transactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "manual");

        SQLException refused =
                expectFailure(migrator, "UPDATE app.transactions SET source = 'IMPORTED' WHERE id = ?", transactionId);

        assertThat(refused.getSQLState()).isEqualTo(RAISE_EXCEPTION);
        assertThat(refused.getMessage()).contains("cannot change source on a transaction");
    }

    @Test
    void manualTransactionAmountAndNarrationAreEditable() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long transactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "original narration");

        execute(
                migrator,
                "UPDATE app.transactions SET amount_paise = ?, narration = ? WHERE id = ?",
                250_00L,
                "updated narration",
                transactionId);

        try (PreparedStatement statement =
                migrator.prepareStatement("SELECT amount_paise, narration FROM app.transactions WHERE id = ?")) {
            statement.setLong(1, transactionId);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getLong("amount_paise")).isEqualTo(250_00L);
                assertThat(row.getString("narration")).isEqualTo("updated narration");
            }
        }
    }

    /** DM-05: without this, a trigger that froze the whole imported row would still pass every refusal test above. */
    @Test
    void interpretationColumnsRemainEditableOnAnImportedTransaction() throws SQLException {
        Connection migrator = migratorConnection();
        ImportedFixture fixture = createImportedTransaction(migrator, "fixture narration", 100_00L);
        long categoryId = TestFixtures.insertCategory(migrator, fixture.userId(), "EXPENSE");

        execute(
                migrator,
                """
                UPDATE app.transactions
                SET transaction_type = 'EXPENSE', category_source = 'MANUAL', category_id = ?, needs_wants = 'NEED', notes = ?
                WHERE id = ?
                """,
                categoryId,
                "reviewed",
                fixture.transactionId());

        try (PreparedStatement statement = migrator.prepareStatement(
                "SELECT transaction_type, category_id, needs_wants, notes FROM app.transactions WHERE id = ?")) {
            statement.setLong(1, fixture.transactionId());
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("transaction_type")).isEqualTo("EXPENSE");
                assertThat(row.getLong("category_id")).isEqualTo(categoryId);
                assertThat(row.getString("needs_wants")).isEqualTo("NEED");
                assertThat(row.getString("notes")).isEqualTo("reviewed");
            }
        }
    }

    @Test
    void importedTransactionCannotBeDeletedWhileItsImportIsCommitted() throws SQLException {
        Connection migrator = migratorConnection();
        ImportedFixture fixture = createImportedTransaction(migrator, "fixture narration", 100_00L);

        SQLException refused = expectFailure(migrator, "DELETE FROM app.transactions WHERE id = ?", fixture.transactionId());

        assertThat(refused.getSQLState()).isEqualTo(RAISE_EXCEPTION);
        assertThat(refused.getMessage())
                .contains("cannot delete an IMPORTED transaction unless its statement import is REPLACED");
    }

    @Test
    void importedTransactionCanBeDeletedOnceItsImportIsReplaced() throws SQLException {
        Connection migrator = migratorConnection();
        ImportedFixture fixture = createImportedTransaction(migrator, "fixture narration", 100_00L);

        execute(migrator, "UPDATE app.statement_imports SET status = 'REPLACED' WHERE id = ?", fixture.statementImportId());
        execute(migrator, "DELETE FROM app.transactions WHERE id = ?", fixture.transactionId());

        assertThat(scalar(migrator, "SELECT count(*) FROM app.transactions WHERE id = ?", fixture.transactionId())).isZero();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("protectedImportRowColumns")
    void protectedColumnCannotBeChangedOnAnImportRow(String column, String sqlFragment, Object newValue) throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        long importRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);

        SQLException refused = expectFailure(
                migrator,
                "UPDATE app.statement_import_rows SET " + sqlFragment + " WHERE id = ?",
                newValue,
                importRowId);

        assertThat(refused.getSQLState()).isEqualTo(RAISE_EXCEPTION);
        assertThat(refused.getMessage()).contains("cannot change " + column + " on a statement_import_rows row");
    }

    static Stream<Arguments> protectedImportRowColumns() {
        return Stream.of(
                Arguments.of("user_id", "user_id = ?", 999_999_999L),
                Arguments.of("statement_import_id", "statement_import_id = ?", 999_999_999L),
                Arguments.of("row_number", "row_number = ?", 99),
                Arguments.of("raw_cells", "raw_cells = ?::jsonb", "{\"date\": \"2099-01-01\"}"));
    }

    @Test
    void importRowsRowStatusAndRelatedTransactionRemainEditable() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        long importRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);
        long relatedTransactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 50_00L, "related");

        execute(
                migrator,
                "UPDATE app.statement_import_rows SET row_status = 'DUPLICATE', related_transaction_id = ? WHERE id = ?",
                relatedTransactionId,
                importRowId);

        try (PreparedStatement statement = migrator.prepareStatement(
                "SELECT row_status, related_transaction_id FROM app.statement_import_rows WHERE id = ?")) {
            statement.setLong(1, importRowId);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getString("row_status")).isEqualTo("DUPLICATE");
                assertThat(row.getLong("related_transaction_id")).isEqualTo(relatedTransactionId);
            }
        }
    }

    @Test
    void importRowCannotBeDeletedUnlessItsImportIsHeld() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "COMMITTED");
        long importRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);

        SQLException refused = expectFailure(migrator, "DELETE FROM app.statement_import_rows WHERE id = ?", importRowId);

        assertThat(refused.getSQLState()).isEqualTo(RAISE_EXCEPTION);
        assertThat(refused.getMessage()).contains("cannot delete a statement_import_rows row unless its import is HELD");
    }

    @Test
    void importRowCanBeDeletedWhileItsImportIsHeld() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        long importRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);

        execute(migrator, "DELETE FROM app.statement_import_rows WHERE id = ?", importRowId);

        assertThat(scalar(migrator, "SELECT count(*) FROM app.statement_import_rows WHERE id = ?", importRowId)).isZero();
    }

    /** SR-25: the message reaches the log, so it must name the rule only, never the value that broke it. */
    @Test
    void errorMessagesNeverContainAValue() throws SQLException {
        Connection migrator = migratorConnection();
        long distinctiveAmountPaise = 7_531_598_00L;
        String distinctiveNarration = "TOTALLY-CONFIDENTIAL-NARRATION-4821";
        ImportedFixture fixture = createImportedTransaction(migrator, distinctiveNarration, distinctiveAmountPaise);

        SQLException refused = expectFailure(
                migrator,
                "UPDATE app.transactions SET amount_paise = ? WHERE id = ?",
                distinctiveAmountPaise + 1,
                fixture.transactionId());

        assertThat(refused.getMessage())
                .as("SR-25: the trigger message must never contain a value, only the rule")
                .doesNotContain(String.valueOf(distinctiveAmountPaise))
                .doesNotContain(distinctiveNarration);
    }

    private static ImportedFixture createImportedTransaction(Connection migrator, String narration, long amountPaise)
            throws SQLException {
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "COMMITTED");
        long sourceRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);
        long transactionId = TestFixtures.insertImportedTransaction(
                migrator, userId, accountId, statementImportId, sourceRowId, amountPaise, narration);
        return new ImportedFixture(userId, accountId, statementImportId, sourceRowId, transactionId);
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

    private record ImportedFixture(long userId, long accountId, long statementImportId, long sourceRowId, long transactionId) {
    }
}
