package com.financetracker.common.tenant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

import com.financetracker.db.PostgresTestContainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;

/**
 * The tests that decide whether tenant isolation actually works in the running application.
 *
 * The important one is {@link #shouldApplyTheTenantToTheConnectionHibernateUses()}.
 * "A repository returned rows" can pass for the wrong reason; reading {@code current_setting} back through the same EntityManager cannot.
 */
@SpringBootTest
class TenantTransactionTest {

    private static final String SEEDED_EMAIL = "owner@financetracker.local";

    private static long ownerId;

    private static long otherUserId;

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresTestContainer.APP_USER);
        registry.add("spring.datasource.password", () -> PostgresTestContainer.APP_PASSWORD);
        registry.add("spring.flyway.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.flyway.user", () -> PostgresTestContainer.MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> PostgresTestContainer.MIGRATOR_PASSWORD);
    }

    private static boolean tenantsCreated;

    /**
     * A second tenant with exactly one account, so "sees only its own rows" is a different number for each user rather than empty against non-empty.
     * Written as the migrator, which is the only role that can create a row for a user other than itself, and committed because the application reads it on another connection.
     *
     * Runs before each test rather than once before all of them: the Spring context, and therefore Flyway, has not started yet at {@code @BeforeAll}, so the tables would not exist when this class runs on its own.
     */
    @BeforeEach
    void createSecondTenantOnce() throws SQLException {
        if (tenantsCreated) {
            return;
        }
        createSecondTenant();
        tenantsCreated = true;
    }

    private static void createSecondTenant() throws SQLException {
        try (Connection migrator = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(),
                PostgresTestContainer.MIGRATOR_USER,
                PostgresTestContainer.MIGRATOR_PASSWORD)) {

            ownerId = queryForLong(migrator, "SELECT id FROM app.users WHERE email = ?", SEEDED_EMAIL);
            otherUserId = queryForLong(
                    migrator,
                    "INSERT INTO app.users (email, display_name) VALUES (?, 'Tenant B') RETURNING id",
                    "tenant-b-" + System.nanoTime() + "@example.invalid");
            queryForLong(
                    migrator,
                    """
                    INSERT INTO app.accounts (user_id, name, type, dedup_method)
                    VALUES (?, 'Tenant B Savings', 'ASSET', 'ROW_FINGERPRINT') RETURNING id
                    """,
                    otherUserId);
        }
    }

    @Autowired
    private PlatformTransactionManager transactionManager;

    @Autowired
    private TenantProbe probe;

    @Test
    void shouldUseTheTenantAwareTransactionManager() {
        assertThat(transactionManager).isInstanceOf(TenantAwareJpaTransactionManager.class);
    }

    @Test
    void shouldApplyTheTenantToTheConnectionHibernateUses() {
        String setting = CurrentTenantContext.runAs(ownerId, probe::currentTenantSetting);

        assertThat(setting).isEqualTo(Long.toString(ownerId));
    }

    @Test
    void shouldSeeOnlyTheRowsOfTheTenantInScope() {
        long ownerAccounts = CurrentTenantContext.runAs(ownerId, probe::countAccounts);
        long otherAccounts = CurrentTenantContext.runAs(otherUserId, probe::countAccounts);

        assertThat(ownerAccounts).isEqualTo(4);
        assertThat(otherAccounts).isEqualTo(1);
    }

    @Test
    void shouldRefuseATransactionThatHasNoTenantContext() {
        assertThatThrownBy(probe::countAccounts)
                .isInstanceOf(MissingTenantContextException.class)
                .hasMessageContaining("runAs");
    }

    /**
     * A system transaction runs with a tenant no row can have, rather than with no setting at all.
     * Leaving the setting alone would make the policy cast an empty string on any connection that has already carried a tenant, so the query would fail instead of returning nothing.
     */
    @Test
    void shouldSeeNoDomainRowsInASystemTransaction() {
        assertThat(CurrentTenantContext.runAsSystem(probe::currentTenantSetting)).isEqualTo("-1");
        assertThat(CurrentTenantContext.runAsSystem(probe::countAccounts)).isZero();
    }

    /** {@code set_config(..., true)} is transaction-local, so the next transaction on that connection must not see the previous tenant. */
    @Test
    void shouldNotLeaveTheTenantOnTheConnectionAfterTheTransactionEnds() {
        CurrentTenantContext.runAs(ownerId, probe::countAccounts);

        assertThat(CurrentTenantContext.runAsSystem(probe::currentTenantSetting)).isNotEqualTo(Long.toString(ownerId));
        assertThat(CurrentTenantContext.runAsSystem(probe::countAccounts)).isZero();
    }

    @Test
    void shouldLeaveNothingOnTheThreadAfterTheScopeEnds() {
        CurrentTenantContext.runAs(ownerId, probe::countAccounts);

        assertThat(CurrentTenantContext.currentUserId()).isNull();
        assertThatThrownBy(probe::countAccounts).isInstanceOf(MissingTenantContextException.class);
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

    @TestConfiguration
    static class ProbeConfiguration {

        @Bean
        TenantProbe tenantProbe() {
            return new TenantProbe();
        }
    }

    /**
     * Runs real transactions through the application's transaction manager.
     * Native queries rather than a repository, so the assertions are about the connection and not about JPA mapping.
     */
    public static class TenantProbe {

        @PersistenceContext
        private EntityManager entityManager;

        @Transactional(readOnly = true)
        public String currentTenantSetting() {
            return (String) entityManager
                    .createNativeQuery("select current_setting('app.user_id', true)")
                    .getSingleResult();
        }

        @Transactional(readOnly = true)
        public long countAccounts() {
            return ((Number) entityManager
                            .createNativeQuery("select count(*) from app.accounts")
                            .getSingleResult())
                    .longValue();
        }
    }
}
