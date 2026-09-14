package com.financetracker.common.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Refuses to start the {@code prod} profile with configuration that would otherwise run insecurely instead of failing (SECURITY.md SR-35, SR-36, SR-40).
 *
 * A {@link BeanFactoryPostProcessor} runs during bean-factory post-processing, before any singleton bean — including the datasource — is created.
 * That is what makes the failure a clear message about missing configuration, rather than a driver error after an insecure connection was already attempted.
 */
@Component
public class ProductionEnvironmentGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String PROD_PROFILE = "prod";

    static final String REQUIRED_SSL_MODE = "sslmode=verify-full";

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) {
        validate(environment);
    }

    /** Package-private so the test can drive every branch directly against a throwaway {@link Environment}, without needing a full application context for each case. */
    void validate(Environment environment) {
        if (!environment.acceptsProfiles(Profiles.of(PROD_PROFILE))) {
            return;
        }

        String jdbcUrl = environment.getProperty("spring.datasource.url");
        if (jdbcUrl == null || !jdbcUrl.contains(REQUIRED_SSL_MODE)) {
            throw new IllegalStateException(
                    "DB_URL must include " + REQUIRED_SSL_MODE + " under the prod profile (SECURITY.md SR-36)");
        }

        requireConfigured(environment, "ft.owner.email", "FT_OWNER_EMAIL");
        requireConfigured(environment, "ft.owner.password", "FT_OWNER_PASSWORD");
    }

    private static void requireConfigured(Environment environment, String property, String envVarName) {
        if (!StringUtils.hasText(environment.getProperty(property))) {
            throw new IllegalStateException(
                    envVarName + " must be set under the prod profile (SECURITY.md SR-35, SR-40)");
        }
    }
}
