package com.financetracker.common.security;

import javax.sql.DataSource;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.session.jdbc.config.annotation.SpringSessionTransactionManager;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * The transaction manager Spring Session JDBC uses to read and write {@code auth.spring_session} rows.
 *
 * It is deliberately not {@code TenantAwareJpaTransactionManager}.
 * That manager refuses to begin a transaction with no tenant in scope, but a session is read before any tenant is known.
 * Reading it is how the tenant gets established for the rest of the request.
 * A transaction manager with that guarantee would fail on every request, including the very first one that has no session yet.
 *
 * {@code auth.spring_session} carries no Row-Level Security for the same reason (D-34), so a plain transaction manager over the same connection pool is correct here.
 * The {@code @SpringSessionTransactionManager} qualifier is Spring Session's own way to pick this bean over the tenant-aware one once two candidates exist.
 * Without it, autowiring the session repository would fail with an unresolvable ambiguity, not silently pick either one.
 */
@Configuration(proxyBeanMethods = false)
class SessionStoreConfiguration {

    @Bean
    @SpringSessionTransactionManager
    PlatformTransactionManager sessionRepositoryTransactionManager(DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}
