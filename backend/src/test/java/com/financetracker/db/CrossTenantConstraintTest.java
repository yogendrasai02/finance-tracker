package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/**
 * Proves DM-23: every foreign key to a user-owned parent carries user_id, so a row for one user cannot reference another user's row, even when the plain id would otherwise match.
 */
class CrossTenantConstraintTest extends SchemaTestBase {

    private static final String FOREIGN_KEY_VIOLATION = "23503";
    private static final LocalDate TXN_DATE = LocalDate.of(2026, 1, 1);

    @Test
    void transactionForACannotReferenceAnAccountOwnedByB() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long userB = TestFixtures.insertUser(migrator);
        long accountB = TestFixtures.insertAccount(migrator, userB);

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions (user_id, account_id, txn_date, amount_paise, narration, source) "
                        + "VALUES (?, ?, ?, 100, 'cross tenant', 'MANUAL')",
                userA, accountB, TXN_DATE);

        assertRefusedBy(refused, "fk_transactions_account");
    }

    @Test
    void transactionForACannotReferenceACategoryOwnedByB() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long accountA = TestFixtures.insertAccount(migrator, userA);
        long userB = TestFixtures.insertUser(migrator);
        long categoryB = TestFixtures.insertCategory(migrator, userB, "EXPENSE");

        SQLException refused = expectFailure(
                migrator,
                "INSERT INTO app.transactions "
                        + "(user_id, account_id, txn_date, amount_paise, narration, source, transaction_type, category_id, category_source) "
                        + "VALUES (?, ?, ?, 100, 'cross tenant', 'MANUAL', 'EXPENSE', ?, 'MANUAL')",
                userA, accountA, TXN_DATE, categoryB);

        assertRefusedBy(refused, "fk_transactions_category");
    }

    @Test
    void categoryRuleForACannotReferenceBsCategory() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long userB = TestFixtures.insertUser(migrator);
        long categoryB = TestFixtures.insertCategory(migrator, userB, "EXPENSE");

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertCategoryRule(c, userA, null, categoryB));

        assertRefusedBy(refused, "fk_category_rules_category");
    }

    @Test
    void categoryRuleForACannotReferenceBsAccount() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long categoryA = TestFixtures.insertCategory(migrator, userA, "EXPENSE");
        long userB = TestFixtures.insertUser(migrator);
        long accountB = TestFixtures.insertAccount(migrator, userB);

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertCategoryRule(c, userA, accountB, categoryA));

        assertRefusedBy(refused, "fk_category_rules_account");
    }

    @Test
    void statementImportForACannotReferenceBsAccount() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long userB = TestFixtures.insertUser(migrator);
        long accountB = TestFixtures.insertAccount(migrator, userB);

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertStatementImport(c, userA, accountB, "HELD"));

        assertRefusedBy(refused, "fk_statement_imports_account");
    }

    @Test
    void rawImportRowForACannotReferenceBsStatementImport() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long userB = TestFixtures.insertUser(migrator);
        long accountB = TestFixtures.insertAccount(migrator, userB);
        long statementImportB = TestFixtures.insertStatementImport(migrator, userB, accountB, "HELD");

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertImportRow(c, userA, statementImportB, 1));

        assertRefusedBy(refused, "fk_import_rows_statement_import");
    }

    @Test
    void linkMemberCannotJoinAsLinkToBsTransaction() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long linkA = TestFixtures.insertTransactionLink(migrator, userA, "REIMBURSEMENT", null);
        long userB = TestFixtures.insertUser(migrator);
        long accountB = TestFixtures.insertAccount(migrator, userB);
        long transactionB = TestFixtures.insertManualTransaction(migrator, userB, accountB, 100_00L, "b's transaction");

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertTransactionLinkMember(c, userA, linkA, transactionB));

        assertRefusedBy(refused, "fk_link_members_transaction");
    }

    @Test
    void balanceCheckpointForACannotReferenceBsAccount() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long userB = TestFixtures.insertUser(migrator);
        long accountB = TestFixtures.insertAccount(migrator, userB);

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertBalanceCheckpoint(c, userA, accountB));

        assertRefusedBy(refused, "fk_balance_checkpoints_account");
    }

    private static void assertRefusedBy(SQLException refused, String constraintName) {
        assertThat(refused.getSQLState()).isEqualTo(FOREIGN_KEY_VIOLATION);
        assertThat(refused.getMessage()).contains(constraintName);
    }
}
