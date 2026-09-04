package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Proves the row-level rules `V1` puts on {@code app.transactions} (§5.6, DM-06, D-20).
 * Each refusal checks the constraint name in the error, so a test cannot pass because a different rule happened to reject the row.
 */
class TransactionConstraintTest extends SchemaTestBase {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";
    private static final String FOREIGN_KEY_VIOLATION = "23503";

    private static final LocalDate TXN_DATE = LocalDate.of(2026, 1, 1);

    // ---- refusals ----

    @Test
    void amountOfZeroIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions (user_id, account_id, txn_date, amount_paise, narration, source) "
                        + "VALUES (?, ?, ?, 0, 'zero amount', 'MANUAL')",
                f.userId(), f.accountId(), TXN_DATE);

        assertRefusedBy(refused, "chk_transactions_amount_nonzero", CHECK_VIOLATION);
    }

    @Test
    void aSecondTransactionWithAnExistingFingerprintIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");
        long firstRowId = TestFixtures.insertImportRow(migrator, f.userId(), statementImportId, 1);
        long secondRowId = TestFixtures.insertImportRow(migrator, f.userId(), statementImportId, 2);
        TestFixtures.insertImportedTransaction(
                migrator, f.userId(), f.accountId(), statementImportId, firstRowId, 100_00L, "first", "shared-fingerprint", 1);

        SQLException refused = expectFailure(
                migrator,
                c -> TestFixtures.insertImportedTransaction(
                        c, f.userId(), f.accountId(), statementImportId, secondRowId, 200_00L, "second",
                        "shared-fingerprint", 1));

        assertRefusedBy(refused, "ux_transactions_fingerprint", UNIQUE_VIOLATION);
    }

    @Test
    void transferCarryingACategoryIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, 100, 'transfer', 'MANUAL', 'TRANSFER', ?, 'MANUAL')",
                f.userId(), f.accountId(), TXN_DATE, f.expenseCategoryId());

        assertRefusedBy(refused, "fk_transactions_category", FOREIGN_KEY_VIOLATION);
    }

    @Test
    void unclassifiedRowCarryingACategoryIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, 100, 'unclassified', 'MANUAL', 'UNCLASSIFIED', ?, 'MANUAL')",
                f.userId(), f.accountId(), TXN_DATE, f.expenseCategoryId());

        assertRefusedBy(refused, "fk_transactions_category", FOREIGN_KEY_VIOLATION);
    }

    @Test
    void expenseTransactionGivenAnIncomeCategoryIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, 100, 'mismatched', 'MANUAL', 'EXPENSE', ?, 'MANUAL')",
                f.userId(), f.accountId(), TXN_DATE, f.incomeCategoryId());

        assertRefusedBy(refused, "fk_transactions_category", FOREIGN_KEY_VIOLATION);
    }

    @Test
    void incomeTransactionGivenAnExpenseCategoryIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, 100, 'mismatched', 'MANUAL', 'INCOME', ?, 'MANUAL')",
                f.userId(), f.accountId(), TXN_DATE, f.expenseCategoryId());

        assertRefusedBy(refused, "fk_transactions_category", FOREIGN_KEY_VIOLATION);
    }

    @Test
    void needsWantsSetOnAnythingOtherThanExpenseIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, needs_wants) "
                        + "VALUES (?, ?, ?, 100, 'income', 'MANUAL', 'INCOME', 'NEED')",
                f.userId(), f.accountId(), TXN_DATE);

        assertRefusedBy(refused, "chk_needs_wants_expense_only", CHECK_VIOLATION);
    }

    @Test
    void categoryIdWithoutCategorySourceIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id) "
                        + "VALUES (?, ?, ?, 100, 'unpaired', 'MANUAL', 'EXPENSE', ?)",
                f.userId(), f.accountId(), TXN_DATE, f.expenseCategoryId());

        assertRefusedBy(refused, "chk_category_source_paired", CHECK_VIOLATION);
    }

    @Test
    void categorySourceWithoutCategoryIdIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_source) "
                        + "VALUES (?, ?, ?, 100, 'unpaired', 'MANUAL', 'EXPENSE', 'MANUAL')",
                f.userId(), f.accountId(), TXN_DATE);

        assertRefusedBy(refused, "chk_category_source_paired", CHECK_VIOLATION);
    }

    @Test
    void categoryRuleIdWhenCategorySourceIsManualIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long categoryRuleId = TestFixtures.insertCategoryRule(migrator, f.userId(), null, f.expenseCategoryId());

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, "
                        + "category_id, category_source, category_rule_id) "
                        + "VALUES (?, ?, ?, 100, 'rule on manual', 'MANUAL', 'EXPENSE', ?, 'MANUAL', ?)",
                f.userId(), f.accountId(), TXN_DATE, f.expenseCategoryId(), categoryRuleId);

        assertRefusedBy(refused, "chk_category_source_paired", CHECK_VIOLATION);
    }

    @Test
    void importedTransactionWithNoStatementImportIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");
        long sourceRowId = TestFixtures.insertImportRow(migrator, f.userId(), statementImportId, 1);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, source_row_id) "
                        + "VALUES (?, ?, ?, 100, 'no import', 'IMPORTED', ?)",
                f.userId(), f.accountId(), TXN_DATE, sourceRowId);

        assertRefusedBy(refused, "chk_imported_has_source", CHECK_VIOLATION);
    }

    @Test
    void importedTransactionWithNoSourceRowIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, statement_import_id) "
                        + "VALUES (?, ?, ?, 100, 'no source row', 'IMPORTED', ?)",
                f.userId(), f.accountId(), TXN_DATE, statementImportId);

        assertRefusedBy(refused, "chk_imported_has_source", CHECK_VIOLATION);
    }

    @Test
    void importedTransactionWithNoNarrationIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");
        long sourceRowId = TestFixtures.insertImportRow(migrator, f.userId(), statementImportId, 1);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, source, statement_import_id, source_row_id) "
                        + "VALUES (?, ?, ?, 100, 'IMPORTED', ?, ?)",
                f.userId(), f.accountId(), TXN_DATE, statementImportId, sourceRowId);

        assertRefusedBy(refused, "chk_imported_has_source", CHECK_VIOLATION);
    }

    @Test
    void manualTransactionCarryingAFingerprintIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, source_row_fingerprint, fingerprint_version) "
                        + "VALUES (?, ?, ?, 100, 'manual with fingerprint', 'MANUAL', 'should-not-be-set', 1)",
                f.userId(), f.accountId(), TXN_DATE);

        assertRefusedBy(refused, "chk_non_imported_has_no_source", CHECK_VIOLATION);
    }

    @Test
    void manualTransactionCarryingABalanceIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, balance_after_paise) "
                        + "VALUES (?, ?, ?, 100, 'manual with balance', 'MANUAL', 500000)",
                f.userId(), f.accountId(), TXN_DATE);

        assertRefusedBy(refused, "chk_non_imported_has_no_source", CHECK_VIOLATION);
    }

    @Test
    void manualTransactionCarryingAnImportReferenceIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, statement_import_id) "
                        + "VALUES (?, ?, ?, 100, 'manual with import ref', 'MANUAL', ?)",
                f.userId(), f.accountId(), TXN_DATE, statementImportId);

        assertRefusedBy(refused, "chk_non_imported_has_no_source", CHECK_VIOLATION);
    }

    @Test
    void fingerprintWithoutFingerprintVersionIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");
        long sourceRowId = TestFixtures.insertImportRow(migrator, f.userId(), statementImportId, 1);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, "
                        + "statement_import_id, source_row_id, source_row_fingerprint) "
                        + "VALUES (?, ?, ?, 100, 'unversioned fingerprint', 'IMPORTED', ?, ?, 'fp-without-version')",
                f.userId(), f.accountId(), TXN_DATE, statementImportId, sourceRowId);

        assertRefusedBy(refused, "chk_fingerprint_versioned", CHECK_VIOLATION);
    }

    @Test
    void twoTransactionsPointingAtTheSameSourceRowAreRefused() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");
        long sourceRowId = TestFixtures.insertImportRow(migrator, f.userId(), statementImportId, 1);
        TestFixtures.insertImportedTransaction(
                migrator, f.userId(), f.accountId(), statementImportId, sourceRowId, 100_00L, "first");

        SQLException refused = expectFailure(
                migrator,
                c -> TestFixtures.insertImportedTransaction(
                        c, f.userId(), f.accountId(), statementImportId, sourceRowId, 200_00L, "second"));

        assertRefusedBy(refused, "ux_transactions_source_row_id", UNIQUE_VIOLATION);
    }

    // ---- acceptances ----

    @Test
    void expenseRowWithAnExpenseCategoryIsAccepted() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        long transactionId = insertReturningId(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, 500, 'groceries', 'MANUAL', 'EXPENSE', ?, 'MANUAL') RETURNING id",
                f.userId(), f.accountId(), TXN_DATE, f.expenseCategoryId());

        assertThat(transactionType(migrator, transactionId)).isEqualTo("EXPENSE");
    }

    @Test
    void aRefundIsAcceptedAsAnExpenseWithANegativeAmount() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        long transactionId = insertReturningId(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, -500, 'refund', 'MANUAL', 'EXPENSE', ?, 'MANUAL') RETURNING id",
                f.userId(), f.accountId(), TXN_DATE, f.expenseCategoryId());

        assertThat(amountPaise(migrator, transactionId)).isEqualTo(-500L);
    }

    @Test
    void transferWithNoCategoryIsAccepted() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        long transactionId = insertReturningId(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type) "
                        + "VALUES (?, ?, ?, 500, 'transfer', 'MANUAL', 'TRANSFER') RETURNING id",
                f.userId(), f.accountId(), TXN_DATE);

        assertThat(transactionType(migrator, transactionId)).isEqualTo("TRANSFER");
    }

    @Test
    void importedRowWithAllItsSourceColumnsIsAccepted() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);
        long statementImportId = TestFixtures.insertStatementImport(migrator, f.userId(), f.accountId(), "COMMITTED");
        long sourceRowId = TestFixtures.insertImportRow(migrator, f.userId(), statementImportId, 1);

        long transactionId = TestFixtures.insertImportedTransaction(
                migrator, f.userId(), f.accountId(), statementImportId, sourceRowId, 100_00L, "imported row");

        assertThat(scalar(migrator, "SELECT count(*) FROM app.transactions WHERE id = ?", transactionId)).isEqualTo(1);
    }

    @Test
    void aTransactionInsertedWithoutTransactionTypeDefaultsToUnclassified() throws SQLException {
        Connection migrator = migratorConnection();
        Fixture f = fixture(migrator);

        long transactionId = TestFixtures.insertManualTransaction(migrator, f.userId(), f.accountId(), 100_00L, "no type given");

        assertThat(transactionType(migrator, transactionId)).isEqualTo("UNCLASSIFIED");
    }

    // ---- helpers ----

    private static Fixture fixture(Connection migrator) throws SQLException {
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long expenseCategoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");
        long incomeCategoryId = TestFixtures.insertCategory(migrator, userId, "INCOME");
        return new Fixture(userId, accountId, expenseCategoryId, incomeCategoryId);
    }

    private static void assertRefusedBy(SQLException refused, String constraintName, String sqlState) {
        assertThat(refused.getSQLState()).isEqualTo(sqlState);
        assertThat(refused.getMessage()).contains(constraintName);
    }

    private static long insertReturningId(Connection connection, String sql, Object... parameters) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
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

    private static String transactionType(Connection connection, long transactionId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT transaction_type FROM app.transactions WHERE id = ?")) {
            statement.setLong(1, transactionId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getString(1);
            }
        }
    }

    private static long amountPaise(Connection connection, long transactionId) throws SQLException {
        try (PreparedStatement statement =
                connection.prepareStatement("SELECT amount_paise FROM app.transactions WHERE id = ?")) {
            statement.setLong(1, transactionId);
            try (ResultSet row = statement.executeQuery()) {
                row.next();
                return row.getLong(1);
            }
        }
    }

    private record Fixture(long userId, long accountId, long expenseCategoryId, long incomeCategoryId) {
    }
}
