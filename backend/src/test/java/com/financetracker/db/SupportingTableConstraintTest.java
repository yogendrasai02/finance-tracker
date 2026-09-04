package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.LocalDate;

import org.junit.jupiter.api.Test;

/** Proves the row-level rules V1 puts on the tables outside app.transactions. */
class SupportingTableConstraintTest extends SchemaTestBase {

    private static final String CHECK_VIOLATION = "23514";
    private static final String UNIQUE_VIOLATION = "23505";

    // ---- statement_imports ----

    @Test
    void aSecondCommittedImportForTheSameAccountAndStatementDateIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        LocalDate statementDate = LocalDate.of(2026, 1, 31);
        TestFixtures.insertStatementImport(migrator, userId, accountId, "COMMITTED", statementDate);

        SQLException refused = expectFailure(
                migrator, c -> TestFixtures.insertStatementImport(c, userId, accountId, "COMMITTED", statementDate));

        assertRefusedBy(refused, "ux_statement_imports_cc_statement", UNIQUE_VIOLATION);
    }

    @Test
    void twoReplacedImportsForTheSameStatementDateAreAllowed() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        LocalDate statementDate = LocalDate.of(2026, 1, 31);
        TestFixtures.insertStatementImport(migrator, userId, accountId, "REPLACED", statementDate);

        long secondId = TestFixtures.insertStatementImport(migrator, userId, accountId, "REPLACED", statementDate);

        assertThat(secondId).isPositive();
    }

    // ---- statement_import_rows ----

    @Test
    void twoRowsWithTheSameRowNumberInOneImportAreRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long statementImportId = TestFixtures.insertStatementImport(migrator, userId, accountId, "HELD");
        TestFixtures.insertImportRow(migrator, userId, statementImportId, 1);

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertImportRow(c, userId, statementImportId, 1));

        assertRefusedBy(refused, "ux_import_rows_import_row_number", UNIQUE_VIOLATION);
    }

    // ---- dismissed_matches ----

    @Test
    void transactionIdANotLessThanTransactionIdBIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long txnA = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "a");
        long txnB = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "b");
        long lowerId = Math.min(txnA, txnB);
        long higherId = Math.max(txnA, txnB);

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertDismissedMatch(c, userId, "TRANSFER", higherId, lowerId));

        assertRefusedBy(refused, "chk_dismissed_matches_order", CHECK_VIOLATION);
    }

    @Test
    void anExactDuplicatePairIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long txnA = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "a");
        long txnB = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "b");
        long lowerId = Math.min(txnA, txnB);
        long higherId = Math.max(txnA, txnB);
        TestFixtures.insertDismissedMatch(migrator, userId, "TRANSFER", lowerId, higherId);

        SQLException refused = expectFailure(
                migrator, c -> TestFixtures.insertDismissedMatch(c, userId, "TRANSFER", lowerId, higherId));

        assertRefusedBy(refused, "ux_dismissed_matches_pair", UNIQUE_VIOLATION);
    }

    @Test
    void theSamePairIsAllowedUnderADifferentMatchType() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long txnA = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "a");
        long txnB = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "b");
        long lowerId = Math.min(txnA, txnB);
        long higherId = Math.max(txnA, txnB);
        TestFixtures.insertDismissedMatch(migrator, userId, "TRANSFER", lowerId, higherId);

        long secondId = TestFixtures.insertDismissedMatch(migrator, userId, "MERGE", lowerId, higherId);

        assertThat(secondId).isPositive();
    }

    // ---- transaction_links ----

    @Test
    void aCounterpartyAccountOnAReimbursementLinkIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertTransactionLink(c, userId, "REIMBURSEMENT", accountId));

        assertRefusedBy(refused, "chk_transaction_links_counterparty", CHECK_VIOLATION);
    }

    @Test
    void aCounterpartyAccountOnATransferLinkIsAllowed() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);

        long linkId = TestFixtures.insertTransactionLink(migrator, userId, "TRANSFER", accountId);

        assertThat(linkId).isPositive();
    }

    // ---- transaction_link_members ----

    @Test
    void addingOneTransactionToTwoLinksIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        long transactionId = TestFixtures.insertManualTransaction(migrator, userId, accountId, 100_00L, "shared");
        long firstLinkId = TestFixtures.insertTransactionLink(migrator, userId, "REIMBURSEMENT", null);
        long secondLinkId = TestFixtures.insertTransactionLink(migrator, userId, "REIMBURSEMENT", null);
        TestFixtures.insertTransactionLinkMember(migrator, userId, firstLinkId, transactionId);

        SQLException refused = expectFailure(
                migrator, c -> TestFixtures.insertTransactionLinkMember(c, userId, secondLinkId, transactionId));

        assertRefusedBy(refused, "ux_transaction_link_members_transaction", UNIQUE_VIOLATION);
    }

    // ---- balance_checkpoints ----

    @Test
    void twoBalanceCheckpointsForTheSameAccountAndDateAreRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        LocalDate asOfDate = LocalDate.of(2026, 1, 31);
        TestFixtures.insertBalanceCheckpoint(migrator, userId, accountId, asOfDate, 100_00L);

        SQLException refused = expectFailure(
                migrator, c -> TestFixtures.insertBalanceCheckpoint(c, userId, accountId, asOfDate, 200_00L));

        assertRefusedBy(refused, "ux_balance_checkpoints_account_date", UNIQUE_VIOLATION);
    }

    // ---- category_rules ----

    @Test
    void aOneCharacterPatternIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertCategoryRule(c, userId, null, categoryId, "a", 1));

        assertRefusedBy(refused, "chk_category_rules_pattern_length", CHECK_VIOLATION);
    }

    @Test
    void a101CharacterPatternIsRefused() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");
        String pattern = "a".repeat(101);

        SQLException refused = expectFailure(
                migrator, c -> TestFixtures.insertCategoryRule(c, userId, null, categoryId, pattern, 1));

        assertRefusedBy(refused, "chk_category_rules_pattern_length", CHECK_VIOLATION);
    }

    @Test
    void aTwoCharacterPatternIsAccepted() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");

        long ruleId = TestFixtures.insertCategoryRule(migrator, userId, null, categoryId, "ab", 1);

        assertThat(ruleId).isPositive();
    }

    @Test
    void aHundredCharacterPatternIsAccepted() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        long categoryId = TestFixtures.insertCategory(migrator, userId, "EXPENSE");
        String pattern = "a".repeat(100);

        long ruleId = TestFixtures.insertCategoryRule(migrator, userId, null, categoryId, pattern, 1);

        assertThat(ruleId).isPositive();
    }

    // ---- accounts and categories ----

    @Test
    void accountsRefuseADuplicateNameForOneUser() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        TestFixtures.insertAccount(migrator, userId, "Checking", "ASSET", "NONE");

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertAccount(c, userId, "Checking", "ASSET", "NONE"));

        assertRefusedBy(refused, "ux_accounts_user_name", UNIQUE_VIOLATION);
    }

    @Test
    void accountsAllowTheSameNameForTwoDifferentUsers() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long userB = TestFixtures.insertUser(migrator);
        TestFixtures.insertAccount(migrator, userA, "Checking", "ASSET", "NONE");

        long accountId = TestFixtures.insertAccount(migrator, userB, "Checking", "ASSET", "NONE");

        assertThat(accountId).isPositive();
    }

    @Test
    void categoriesRefuseADuplicateNameForOneUser() throws SQLException {
        Connection migrator = migratorConnection();
        long userId = TestFixtures.insertUser(migrator);
        TestFixtures.insertCategory(migrator, userId, "Groceries", "EXPENSE");

        SQLException refused =
                expectFailure(migrator, c -> TestFixtures.insertCategory(c, userId, "Groceries", "EXPENSE"));

        assertRefusedBy(refused, "ux_categories_user_name", UNIQUE_VIOLATION);
    }

    @Test
    void categoriesAllowTheSameNameForTwoDifferentUsers() throws SQLException {
        Connection migrator = migratorConnection();
        long userA = TestFixtures.insertUser(migrator);
        long userB = TestFixtures.insertUser(migrator);
        TestFixtures.insertCategory(migrator, userA, "Groceries", "EXPENSE");

        long categoryId = TestFixtures.insertCategory(migrator, userB, "Groceries", "EXPENSE");

        assertThat(categoryId).isPositive();
    }

    private static void assertRefusedBy(SQLException refused, String constraintName, String sqlState) {
        assertThat(refused.getSQLState()).isEqualTo(sqlState);
        assertThat(refused.getMessage()).contains(constraintName);
    }
}
