package com.financetracker.account;

import java.util.List;

import com.financetracker.db.PostgresTestContainer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Boots the JPA context against Testcontainers Postgres to prove two things:
 *
 * 1. Entity mappings are valid against the real schema.
 *    With {@code ddl-auto: validate}, a mapping mismatch fails context startup before tests run.
 *
 * 2. RLS is active through Hibernate.
 *    A {@code findAll()} as {@code ft_app} with no {@code app.user_id} returns zero rows.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class JpaBaselineTest {

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

    @Test
    void shouldReturnEmptyAccountsWhenNoTenantContextIsSet() {
        // Seed data exists, but no app.user_id is set on this connection.
        // The RLS policy on app.accounts makes all rows invisible.
        List<Account> accounts = accountRepository.findAll();

        // RLS is enforced through Hibernate, returning zero rows instead of four.
        assertThat(accounts).isEmpty();
    }
}
