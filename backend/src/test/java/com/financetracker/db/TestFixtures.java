package com.financetracker.db;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Row builders for the schema tests.
 * Each method returns the generated id.
 *
 * These must be called on a migrator connection: the schema owner skips RLS, so it can create rows belonging to any user, which is what the cross-tenant tests need.
 * Values are synthetic and generated unique per call, so no test depends on another test's data.
 */
final class TestFixtures {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    private TestFixtures() {
    }

    static long insertUser(Connection connection) throws SQLException {
        long n = SEQUENCE.incrementAndGet();
        return insertReturningId(
                connection,
                "INSERT INTO app.users (email, display_name) VALUES (?, ?) RETURNING id",
                "fixture-" + n + "@example.invalid",
                "Fixture User " + n);
    }

    static long insertAccount(Connection connection, long userId) throws SQLException {
        return insertAccount(connection, userId, "Account " + SEQUENCE.incrementAndGet(), "ASSET", "ROW_FINGERPRINT");
    }

    static long insertAccount(Connection connection, long userId, String name, String type, String dedupMethod)
            throws SQLException {
        return insertReturningId(
                connection,
                "INSERT INTO app.accounts (user_id, name, type, dedup_method) VALUES (?, ?, ?, ?) RETURNING id",
                userId, name, type, dedupMethod);
    }

    static long insertCategory(Connection connection, long userId, String kind) throws SQLException {
        return insertCategory(connection, userId, "Category " + SEQUENCE.incrementAndGet(), kind);
    }

    static long insertCategory(Connection connection, long userId, String name, String kind) throws SQLException {
        return insertReturningId(
                connection,
                "INSERT INTO app.categories (user_id, name, kind) VALUES (?, ?, ?) RETURNING id",
                userId, name, kind);
    }

    /** accountId may be null, which means the rule applies to every account. */
    static long insertCategoryRule(Connection connection, long userId, Long accountId, long categoryId)
            throws SQLException {
        return insertCategoryRule(
                connection, userId, accountId, categoryId, "pattern-" + SEQUENCE.incrementAndGet(), 1);
    }

    static long insertCategoryRule(
            Connection connection, long userId, Long accountId, long categoryId, String pattern, int priority)
            throws SQLException {
        return insertReturningId(
                connection,
                """
                INSERT INTO app.category_rules (user_id, account_id, narration_pattern, category_id, priority)
                VALUES (?, ?, ?, ?, ?) RETURNING id
                """,
                userId, accountId, pattern, categoryId, priority);
    }

    static long insertStatementImport(Connection connection, long userId, long accountId, String status)
            throws SQLException {
        return insertStatementImport(connection, userId, accountId, status, null);
    }

    static long insertStatementImport(
            Connection connection, long userId, long accountId, String status, LocalDate statementDate)
            throws SQLException {
        long n = SEQUENCE.incrementAndGet();
        return insertReturningId(
                connection,
                """
                INSERT INTO app.statement_imports (user_id, account_id, source_filename, file_sha256, status, statement_date)
                VALUES (?, ?, ?, ?, ?, ?) RETURNING id
                """,
                userId, accountId, "statement-" + n + ".csv", "sha256-" + n, status, statementDate);
    }

    static long insertImportRow(Connection connection, long userId, long statementImportId, int rowNumber)
            throws SQLException {
        return insertReturningId(
                connection,
                """
                INSERT INTO app.statement_import_rows (user_id, statement_import_id, row_number, raw_cells)
                VALUES (?, ?, ?, ?::jsonb) RETURNING id
                """,
                userId, statementImportId, rowNumber, "{\"date\": \"2026-01-01\", \"amount\": \"100.00\"}");
    }

    static long insertImportedTransaction(
            Connection connection,
            long userId,
            long accountId,
            long statementImportId,
            long sourceRowId,
            long amountPaise,
            String narration)
            throws SQLException {
        return insertImportedTransaction(
                connection,
                userId,
                accountId,
                statementImportId,
                sourceRowId,
                amountPaise,
                narration,
                "fingerprint-" + SEQUENCE.incrementAndGet(),
                1);
    }

    /** Takes the fingerprint and its version explicitly, for tests that need two rows to collide on one. */
    static long insertImportedTransaction(
            Connection connection,
            long userId,
            long accountId,
            long statementImportId,
            long sourceRowId,
            long amountPaise,
            String narration,
            String fingerprint,
            int fingerprintVersion)
            throws SQLException {
        return insertReturningId(
                connection,
                """
                INSERT INTO app.transactions (
                    user_id, account_id, txn_date, amount_paise, narration, source,
                    statement_import_id, source_row_id, source_row_fingerprint, fingerprint_version)
                VALUES (?, ?, ?, ?, ?, 'IMPORTED', ?, ?, ?, ?) RETURNING id
                """,
                userId,
                accountId,
                LocalDate.of(2026, 1, 1),
                amountPaise,
                narration,
                statementImportId,
                sourceRowId,
                fingerprint,
                fingerprintVersion);
    }

    static long insertManualTransaction(
            Connection connection, long userId, long accountId, long amountPaise, String narration)
            throws SQLException {
        return insertReturningId(
                connection,
                """
                INSERT INTO app.transactions (user_id, account_id, txn_date, amount_paise, narration, source)
                VALUES (?, ?, ?, ?, ?, 'MANUAL') RETURNING id
                """,
                userId, accountId, LocalDate.of(2026, 1, 1), amountPaise, narration);
    }

    static long insertBalanceCheckpoint(Connection connection, long userId, long accountId) throws SQLException {
        return insertBalanceCheckpoint(connection, userId, accountId, LocalDate.of(2026, 1, 1), 100_00L);
    }

    static long insertBalanceCheckpoint(
            Connection connection, long userId, long accountId, LocalDate asOfDate, long actualBalancePaise)
            throws SQLException {
        return insertReturningId(
                connection,
                """
                INSERT INTO app.balance_checkpoints (user_id, account_id, as_of_date, actual_balance_paise)
                VALUES (?, ?, ?, ?) RETURNING id
                """,
                userId, accountId, asOfDate, actualBalancePaise);
    }

    private static long insertReturningId(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            try (ResultSet resultSet = statement.executeQuery()) {
                if (!resultSet.next()) {
                    throw new IllegalStateException("Fixture insert returned no id: " + sql);
                }
                return resultSet.getLong(1);
            }
        }
    }
}
