package com.financetracker.db;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;

/** Proves V7's rules: a statement format must suit the account's dedup method (DM-42), and a hold reason exists exactly when an import is held (DM-43). */
class StatementImportSupportSchemaTest extends SchemaTestBase {

    private static final String SEEDED_EMAIL = "owner@ft.local";
    private static final String CHECK_VIOLATION = "23514";

    @Test
    void shouldSetTheStatementFormatOnTheThreeSeededImportableAccounts() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            Map<String, String> formats = new HashMap<>();
            try (PreparedStatement statement = migrator.prepareStatement(
                    """
                    SELECT a.name, a.statement_format FROM app.accounts a
                    JOIN app.users u ON u.id = a.user_id
                    WHERE u.email = ?
                    """)) {
                statement.setString(1, SEEDED_EMAIL);
                try (ResultSet rows = statement.executeQuery()) {
                    while (rows.next()) {
                        formats.put(rows.getString("name"), rows.getString("statement_format"));
                    }
                }
            }

            assertThat(formats)
                    .containsEntry("SBI Savings", "SBI_SAVINGS_XLSX")
                    .containsEntry("HDFC Savings", "HDFC_SAVINGS_XLSX")
                    .containsEntry("HDFC Millenia", "HDFC_CC_XLSX")
                    .containsEntry("Investments", null);
        }
    }

    @Test
    void shouldRefuseACardFormatOnARowFingerprintAccount() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            long userId = TestFixtures.insertUser(migrator);
            long accountId = TestFixtures.insertAccount(migrator, userId, "Savings", "ASSET", "ROW_FINGERPRINT");

            SQLException refused = expectFailure(
                    migrator, "UPDATE app.accounts SET statement_format = 'HDFC_CC_XLSX' WHERE id = ?", accountId);

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_accounts_statement_format_dedup");
        }
    }

    @Test
    void shouldRefuseASavingsFormatOnAStatementBatchAccount() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            long userId = TestFixtures.insertUser(migrator);
            long accountId = TestFixtures.insertAccount(migrator, userId, "Card", "LIABILITY", "STATEMENT_BATCH");

            SQLException refused = expectFailure(
                    migrator, "UPDATE app.accounts SET statement_format = 'SBI_SAVINGS_XLSX' WHERE id = ?", accountId);

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_accounts_statement_format_dedup");
        }
    }

    @Test
    void shouldRefuseAnUnknownStatementFormat() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            long userId = TestFixtures.insertUser(migrator);
            long accountId = TestFixtures.insertAccount(migrator, userId);

            SQLException refused = expectFailure(
                    migrator, "UPDATE app.accounts SET statement_format = 'ICICI_CC_PDF' WHERE id = ?", accountId);

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            // Quoted, because the unquoted name is also a prefix of the pairing check's name.
            assertThat(refused.getMessage()).contains("\"chk_accounts_statement_format\"");
        }
    }

    @Test
    void shouldRefuseAHeldImportWithNoHoldReason() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            SQLException refused = expectFailure(migrator, c -> insertImport(c, "HELD", null, null));

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_statement_imports_hold_reason_paired");
        }
    }

    @Test
    void shouldRefuseAHoldReasonOnACommittedImport() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            SQLException refused =
                    expectFailure(migrator, c -> insertImport(c, "COMMITTED", "BALANCE_CHAIN_BROKEN", null));

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_statement_imports_hold_reason_paired");
        }
    }

    @Test
    void shouldRefuseAHoldRowNumberWithNoHoldReason() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            SQLException refused = expectFailure(migrator, c -> insertImport(c, "COMMITTED", null, 57));

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            assertThat(refused.getMessage()).contains("chk_statement_imports_hold_row_number_paired");
        }
    }

    @Test
    void shouldRefuseAnUnknownHoldReason() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            SQLException refused = expectFailure(migrator, c -> insertImport(c, "HELD", "LOOKED_WRONG", null));

            assertThat(refused.getSQLState()).isEqualTo(CHECK_VIOLATION);
            // Quoted, because the unquoted name is also a prefix of the pairing check's name.
            assertThat(refused.getMessage()).contains("\"chk_statement_imports_hold_reason\"");
        }
    }

    @Test
    void shouldAcceptAHeldImportWithAReasonAndARowNumber() throws SQLException {
        try (Connection migrator = migratorConnection()) {
            long accountId = insertImport(migrator, "HELD", "BALANCE_CHAIN_BROKEN", 57);

            try (PreparedStatement statement = migrator.prepareStatement(
                    "SELECT hold_reason, hold_row_number FROM app.statement_imports WHERE account_id = ?")) {
                statement.setLong(1, accountId);
                try (ResultSet row = statement.executeQuery()) {
                    assertThat(row.next()).isTrue();
                    assertThat(row.getString("hold_reason")).isEqualTo("BALANCE_CHAIN_BROKEN");
                    assertThat(row.getInt("hold_row_number")).isEqualTo(57);
                }
            }
        }
    }

    /** Inserts an import for a fresh user and account, and returns the account id. */
    private static long insertImport(Connection migrator, String status, String holdReason, Integer holdRowNumber)
            throws SQLException {
        long userId = TestFixtures.insertUser(migrator);
        long accountId = TestFixtures.insertAccount(migrator, userId);
        execute(
                migrator,
                """
                INSERT INTO app.statement_imports (user_id, account_id, source_filename, file_sha256, status, hold_reason, hold_row_number)
                VALUES (?, ?, 'statement.xlsx', 'sha256', ?, ?, ?)
                """,
                userId, accountId, status, holdReason, holdRowNumber);
        return accountId;
    }
}
