package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Proves the DM-24 delete rules actually behave as chosen: RESTRICT on the ledger, CASCADE only for a join child with its parent, SET NULL for a candidate pointer (DM-22, §7.5). */
class DeleteRuleTest extends SchemaTestBase {

    private static final String RESTRICT_VIOLATION = "23001";
    private static final String RAISE_EXCEPTION = "P0001";
    private static final LocalDate TXN_DATE = LocalDate.of(2026, 1, 1);

    // ---- RESTRICT on the ledger ----

    @Test
    void aTransactionThatIsALinkMemberCannotBeDeleted() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long transactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "member");
        long linkId = TestFixtures.insertTransactionLink(migrator, userId, "REIMBURSEMENT", null);
        TestFixtures.insertTransactionLinkMember(migrator, userId, linkId, transactionId);

        SQLException refused = expectFailure(migrator, "DELETE FROM app.transactions WHERE id = ?", transactionId);

        assertRefusedBy(refused, "fk_link_members_transaction");
    }

    @Test
    void anAccountWithTransactionsCannotBeDeleted() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "keeps the account alive");

        SQLException refused = expectFailure(migrator, "DELETE FROM app.accounts WHERE id = ?", accountId);

        assertRefusedBy(refused, "fk_transactions_account");
    }

    @Test
    void aStatementImportWithRawRowsCannotBeDeleted() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);

        SQLException refused =
                expectFailure(migrator, "DELETE FROM app.statement_imports WHERE id = ?", statementImportId);

        assertRefusedBy(refused, "fk_import_rows_statement_import");
    }

    // ---- RESTRICT is what makes is_active real (DM-15) ----

    @Test
    void aCategoryReferencedByATransactionCannotBeDeleted() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");
        insertReturningId(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, 500, 'groceries', 'MANUAL', 'EXPENSE', ?, 'MANUAL') RETURNING id",
                userId, accountId, TXN_DATE, categoryId);

        SQLException refused = expectFailure(migrator, "DELETE FROM app.categories WHERE id = ?", categoryId);

        assertRefusedBy(refused, "fk_transactions_category");
    }

    @Test
    void aCategoryRuleReferencedByATransactionCannotBeDeleted() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");
        long categoryRuleId = TestFixtures.insertCategoryRule(migrator, userId, null, categoryId);
        insertReturningId(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, "
                        + "category_id, category_source, category_rule_id) "
                        + "VALUES (?, ?, ?, 500, 'auto-categorized', 'MANUAL', 'EXPENSE', ?, 'RULE', ?) RETURNING id",
                userId, accountId, TXN_DATE, categoryId, categoryRuleId);

        SQLException refused = expectFailure(migrator, "DELETE FROM app.category_rules WHERE id = ?", categoryRuleId);

        assertRefusedBy(refused, "fk_transactions_category_rule");
    }

    // ---- CASCADE where a child cannot outlive its parent ----

    @Test
    void deletingATransactionLinkRemovesItsMembers() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long transactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "member");
        long linkId = TestFixtures.insertTransactionLink(migrator, userId, "REIMBURSEMENT", null);
        TestFixtures.insertTransactionLinkMember(migrator, userId, linkId, transactionId);

        execute(migrator, "DELETE FROM app.transaction_links WHERE id = ?", linkId);

        assertThat(scalar(migrator, "SELECT count(*) FROM app.transaction_link_members WHERE link_id = ?", linkId))
                .isZero();
        assertThat(scalar(migrator, "SELECT count(*) FROM app.transactions WHERE id = ?", transactionId))
                .as("the member transaction itself is untouched")
                .isEqualTo(1);
    }

    @Test
    void deletingATransactionRemovesTheDismissedMatchesThatReferenceIt() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long txnA = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "a");
        long txnB = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "b");
        long lowerId = Math.min(txnA, txnB);
        long higherId = Math.max(txnA, txnB);
        long dismissedMatchId = TestFixtures.insertDismissedMatch(migrator, userId, "TRANSFER", lowerId, higherId);

        execute(migrator, "DELETE FROM app.transactions WHERE id = ?", lowerId);

        assertThat(scalar(migrator, "SELECT count(*) FROM app.dismissed_matches WHERE id = ?", dismissedMatchId))
                .isZero();
    }

    // ---- SET NULL for the candidate pointer ----

    @Test
    void deletingATransactionClearsTheRelatedTransactionPointerAndLeavesTheRawRowInPlace() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long relatedTransactionId =
                TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "candidate match");
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        long importRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);
        execute(
                migrator,
                "UPDATE app.statement_import_rows SET related_transaction_id = ? WHERE id = ?",
                relatedTransactionId,
                importRowId);

        execute(migrator, "DELETE FROM app.transactions WHERE id = ?", relatedTransactionId);

        try (PreparedStatement statement = migrator.prepareStatement(
                "SELECT related_transaction_id FROM app.statement_import_rows WHERE id = ?")) {
            statement.setLong(1, importRowId);
            try (ResultSet row = statement.executeQuery()) {
                assertThat(row.next()).isTrue();
                assertThat(row.getObject("related_transaction_id")).isNull();
            }
        }
    }

    // ---- the interaction: the immutability trigger runs before any cascade can ----

    @Test
    void deletingAnImportedCommittedTransactionReferencedByADismissedMatchIsRefusedBeforeTheCascadeRuns() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "COMMITTED");
        long sourceRowId = TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);
        long importedTransactionId = TestFixtures.insertImportedTransaction(
                migrator, userId, accountId, statementImportId, sourceRowId, 100_00L, "imported");
        long otherTransactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "other");
        long lowerId = Math.min(importedTransactionId, otherTransactionId);
        long higherId = Math.max(importedTransactionId, otherTransactionId);
        long dismissedMatchId = TestFixtures.insertDismissedMatch(migrator, userId, "TRANSFER", lowerId, higherId);

        SQLException refused =
                expectFailure(migrator, "DELETE FROM app.transactions WHERE id = ?", importedTransactionId);

        assertThat(refused.getSQLState()).isEqualTo(RAISE_EXCEPTION);
        assertThat(refused.getMessage())
                .contains("cannot delete an IMPORTED transaction unless its statement import is REPLACED");
        assertThat(scalar(migrator, "SELECT count(*) FROM app.dismissed_matches WHERE id = ?", dismissedMatchId))
                .as("the trigger refused the delete before the CASCADE on dismissed_matches could run")
                .isEqualTo(1);
    }

    private static void assertRefusedBy(SQLException refused, String constraintName) {
        assertThat(refused.getSQLState()).isEqualTo(RESTRICT_VIOLATION);
        assertThat(refused.getMessage()).contains(constraintName);
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
}
