package com.financetracker.common.tenant;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Replaces Spring Boot's auto-configured transaction manager, which backs off because this bean exists.
 * Defining it here rather than annotating services is what makes tenant scoping structural instead of a habit.
 *
 * {@code @Primary} because a second {@code PlatformTransactionManager} exists for the session store (common.security.SessionStoreConfiguration).
 * Every {@code @Transactional} method in this codebase names no manager, so without a primary, that ordinary usage would fail with an unresolvable ambiguity rather than picking either one silently.
 */
@Configuration(proxyBeanMethods = false)
public class TenantConfiguration {

    @Bean
    @Primary
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory);
    }
}
