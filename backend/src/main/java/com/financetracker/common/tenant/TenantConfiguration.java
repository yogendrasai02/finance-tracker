package com.financetracker.common.tenant;

import jakarta.persistence.EntityManagerFactory;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Replaces Spring Boot's auto-configured transaction manager, which backs off because this bean exists.
 * Defining it here rather than annotating services is what makes tenant scoping structural instead of a habit.
 */
@Configuration(proxyBeanMethods = false)
public class TenantConfiguration {

    @Bean
    PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        return new TenantAwareJpaTransactionManager(entityManagerFactory);
    }
}
