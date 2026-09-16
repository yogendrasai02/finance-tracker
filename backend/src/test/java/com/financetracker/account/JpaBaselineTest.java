package com.financetracker.account;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.util.List;

import com.financetracker.common.tenant.CurrentTenantContext;
import com.financetracker.db.PostgresTestContainer;
import com.financetracker.statement.StatementImport;
import com.financetracker.statement.StatementImportRow;
import com.financetracker.transaction.Transaction;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

/**
 * Boots the JPA context against Testcontainers Postgres to prove two things:
 *
 * 1. Entity mappings are valid against the real schema.
 *    With {@code ddl-auto: validate}, a mapping mismatch fails context startup before tests run.
 *
 * 2. RLS is active through Hibernate.
 *    A query as {@code ft_app} with no tenant returns zero rows.
 *
 * This slice does not load the tenant-aware transaction manager, and each test's transaction is rolled back.
 * So every test sets {@code app.user_id} itself, with the same call the manager makes.
 *
 * "No tenant" is {@code -1}, the value the manager sends for a system transaction, not an unset setting.
 * Once a pooled connection has carried a tenant, Postgres reads the unset setting back as an empty string, and the policy's cast fails.
 * The truly unset case is proved on a fresh JDBC connection by {@code RowLevelSecurityTest}.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaBaselineTest {

    private static final String SEEDED_EMAIL = "owner@ft.local";

    private static final long NO_TENANT = -1;

    private static boolean importFixtureCreated;

    private static long fixtureUserId;

    private static long fixtureAccountId;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresTestContainer.APP_USER);
        registry.add("spring.datasource.password", () -> PostgresTestContainer.APP_PASSWORD);
        registry.add("spring.flyway.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.flyway.user", () -> PostgresTestContainer.MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> PostgresTestContainer.MIGRATOR_PASSWORD);
    }

    @Autowired
    private AccountRepository accountRepository;

    @Autowired
    private TestEntityManager entityManager;

    /**
     * One committed import, raw row and transaction, so "zero rows with no tenant" is checked against tables that are not empty.
     * Written as the migrator, which skips RLS, and committed because Hibernate reads it on another connection.
     * Runs before each test rather than once before all, because Flyway has not run yet at {@code @BeforeAll}.
     */
    @BeforeEach
    void createImportFixtureOnce() throws SQLException {
        if (importFixtureCreated) {
            return;
        }
        try (Connection migrator = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(),
                PostgresTestContainer.MIGRATOR_USER,
                PostgresTestContainer.MIGRATOR_PASSWORD)) {
            fixtureUserId = queryForLong(
                    migrator,
                    "INSERT INTO app.users (email) VALUES (?) RETURNING id",
                    "jpa-baseline-" + System.nanoTime() + "@example.invalid");
            fixtureAccountId = queryForLong(
                    migrator,
                    """
                    INSERT INTO app.accounts (user_id, name, type, dedup_method, statement_format)
                    VALUES (?, 'Fixture Savings', 'ASSET', 'ROW_FINGERPRINT', 'SBI_SAVINGS_XLSX') RETURNING id
                    """,
                    fixtureUserId);
            long importId = queryForLong(
                    migrator,
                    """
                    INSERT INTO app.statement_imports (user_id, account_id, source_filename, file_sha256, status, committed_at)
                    VALUES (?, ?, 'fixture.xlsx', 'fixture-sha256', 'COMMITTED', now()) RETURNING id
                    """,
                    fixtureUserId, fixtureAccountId);
            long rowId = queryForLong(
                    migrator,
                    """
                    INSERT INTO app.statement_import_rows (user_id, statement_import_id, row_number, raw_cells, row_status)
                    VALUES (?, ?, 1, '{"cells": []}', 'NEW') RETURNING id
                    """,
                    fixtureUserId, importId);
            queryForLong(
                    migrator,
                    """
                    INSERT INTO app.transactions (user_id, account_id, txn_date, amount_paise, narration, source, statement_import_id, source_row_id)
                    VALUES (?, ?, DATE '2026-04-01', -100, 'fixture narration', 'IMPORTED', ?, ?) RETURNING id
                    """,
                    fixtureUserId, fixtureAccountId, importId, rowId);
        }
        importFixtureCreated = true;
    }

    @Test
    void shouldReturnEmptyAccountsWhenNoTenantContextIsSet() {
        // Seed data exists, but this transaction carries no real tenant.
        // The RLS policy on app.accounts makes all rows invisible.
        applyTenant(NO_TENANT);

        List<Account> accounts = accountRepository.findAll();

        // RLS is enforced through Hibernate, returning zero rows instead of four.
        assertThat(accounts).isEmpty();
    }

    @Test
    void shouldReturnNoImportsRowsOrTransactionsWhenNoTenantContextIsSet() {
        applyTenant(NO_TENANT);

        assertThat(findAll(StatementImport.class)).isEmpty();
        assertThat(findAll(StatementImportRow.class)).isEmpty();
        assertThat(findAll(Transaction.class)).isEmpty();
    }

    @Test
    void shouldSeeTheFixtureRowsWhenTheirTenantIsSet() {
        applyTenant(fixtureUserId);

        assertThat(findAll(StatementImport.class)).hasSize(1);
        assertThat(findAll(StatementImportRow.class)).hasSize(1);
        assertThat(findAll(Transaction.class)).hasSize(1);
    }

    @Test
    void shouldReadTheStatementFormatOfTheSeededAccounts() {
        long ownerId = queryOwnerId();
        applyTenant(ownerId);

        List<Account> accounts = accountRepository.findAllByOrderByNameAsc();

        assertThat(accounts)
                .extracting(Account::getName, Account::getStatementFormat)
                .containsExactly(
                        tuple("HDFC Millenia", StatementFormat.HDFC_CC_XLSX),
                        tuple("HDFC Savings", StatementFormat.HDFC_SAVINGS_XLSX),
                        tuple("Investments", null),
                        tuple("SBI Savings", StatementFormat.SBI_SAVINGS_XLSX));
    }

    /**
     * The value is already in the form {@code jsonb} prints, so any difference on the way back comes from Hibernate, not from Postgres.
     * It holds a line break, a rupee sign, and a number with more decimals than a double keeps, which are the parts a re-serialization would change.
     */
    @Test
    void shouldReadBackRawCellsExactlyAsWrittenThroughHibernate() {
        String rawCells = "{\"cells\": [{\"ref\": \"E23\", \"text\": \"283407.26000000001\", \"type\": \"n\", \"header\": \"Closing Balance\"}, "
                + "{\"ref\": \"B23\", \"text\": \"UPI/growws\\n tock/₹\", \"type\": \"s\", \"header\": \"Narration\"}]}";
        applyTenant(fixtureUserId);

        Long rowId = CurrentTenantContext.runAs(fixtureUserId, () -> writeImportWithOneRow(rawCells));
        entityManager.clear();

        StatementImportRow stored = entityManager.find(StatementImportRow.class, rowId);
        assertThat(stored.getRawCells()).isEqualTo(rawCells);
    }

    /** Recorded for the parser: {@code raw_cells} comes back in Postgres's own form, so the stored text is only identical to the written text when the parser writes that form. */
    @Test
    void shouldNormalizeKeyOrderAndWhitespaceInRawCells() {
        applyTenant(fixtureUserId);

        Long rowId = CurrentTenantContext.runAs(
                fixtureUserId, () -> writeImportWithOneRow("{\"cells\":[{\"text\":\"1\",\"ref\":\"A1\"}]}"));
        entityManager.clear();

        StatementImportRow stored = entityManager.find(StatementImportRow.class, rowId);
        assertThat(stored.getRawCells()).isEqualTo("{\"cells\": [{\"ref\": \"A1\", \"text\": \"1\"}]}");
    }

    @Test
    void shouldWriteAnImportedTransactionThroughHibernate() {
        applyTenant(fixtureUserId);

        Long transactionId = CurrentTenantContext.runAs(fixtureUserId, () -> {
            StatementImport statementImport = new StatementImport(fixtureAccountId, "upload.xlsx", "sha256-transaction", "COMMITTED");
            entityManager.persist(statementImport);
            StatementImportRow row = new StatementImportRow(statementImport.getId(), 1, "{\"cells\": []}");
            entityManager.persist(row);
            Transaction transaction = new Transaction(fixtureAccountId, LocalDate.of(2026, 4, 2), -250_00L, "IMPORTED");
            transaction.setNarration("fixture narration");
            transaction.setNarrationNormalized("fixture narration");
            transaction.setBalanceAfterPaise(1_000_00L);
            transaction.setStatementImportId(statementImport.getId());
            transaction.setSourceRowId(row.getId());
            transaction.setSourceRowFingerprint("fingerprint-" + System.nanoTime());
            transaction.setFingerprintVersion((short) 1);
            entityManager.persist(transaction);
            entityManager.flush();
            return transaction.getId();
        });
        entityManager.clear();

        Transaction stored = entityManager.find(Transaction.class, transactionId);
        assertThat(stored.getUserId()).isEqualTo(fixtureUserId);
        assertThat(stored.getAmountPaise()).isEqualTo(-250_00L);
        assertThat(stored.getTransactionType()).isEqualTo("UNCLASSIFIED");
        assertThat(stored.getFingerprintVersion()).isEqualTo((short) 1);
    }

    private Long writeImportWithOneRow(String rawCells) {
        StatementImport statementImport =
                new StatementImport(fixtureAccountId, "upload.xlsx", "sha256-" + System.nanoTime(), "COMMITTED");
        entityManager.persist(statementImport);
        StatementImportRow row = new StatementImportRow(statementImport.getId(), 1, rawCells);
        entityManager.persist(row);
        entityManager.flush();
        return row.getId();
    }

    private <T> List<T> findAll(Class<T> entityType) {
        return entityManager.getEntityManager()
                .createQuery("select e from " + entityType.getSimpleName() + " e", entityType)
                .getResultList();
    }

    private void applyTenant(long userId) {
        entityManager.getEntityManager()
                .createNativeQuery("select set_config('app.user_id', ?1, true)")
                .setParameter(1, Long.toString(userId))
                .getSingleResult();
    }

    private static long queryOwnerId() {
        try (Connection migrator = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(),
                PostgresTestContainer.MIGRATOR_USER,
                PostgresTestContainer.MIGRATOR_PASSWORD)) {
            return queryForLong(migrator, "SELECT id FROM app.users WHERE email = ?", SEEDED_EMAIL);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private static long queryForLong(Connection connection, String sql, Object... parameters) throws SQLException {
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
