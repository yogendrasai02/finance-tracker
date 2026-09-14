package com.financetracker.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;

/** No Spring context for the branch-level cases below, and no Docker anywhere — every case here runs against a plain {@link MockEnvironment} or a minimal {@link ApplicationContextRunner}. */
class ProductionEnvironmentGuardTest {

    private static final String VALID_URL = "jdbc:postgresql://db.example.com:5432/financetracker?sslmode=verify-full";

    private final ProductionEnvironmentGuard guard = new ProductionEnvironmentGuard();

    /**
     * The last two cases below prove the wiring, not just the logic: registering the guard as a real bean and letting Spring's own {@code refresh()} invoke it as a {@link org.springframework.beans.factory.config.BeanFactoryPostProcessor}.
     * This context has no datasource auto-configuration at all, so a passing case proves the guard itself does not block startup, not that a database happened to be reachable.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner().withBean(ProductionEnvironmentGuard.class);

    @Test
    void shouldIgnoreEverythingOutsideTheProdProfile() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles("local");

        guard.validate(environment);
    }

    @Test
    void shouldPassUnderProdWithValidConfiguration() {
        MockEnvironment environment = prodEnvironment(VALID_URL, "owner@ft.example", "a-long-enough-password");

        guard.validate(environment);
    }

    @Test
    void shouldRefuseAMissingDatasourceUrl() {
        MockEnvironment environment = prodEnvironment(null, "owner@ft.example", "a-long-enough-password");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sslmode=verify-full");
    }

    @Test
    void shouldRefuseAnUrlMissingSslModeVerifyFull() {
        MockEnvironment environment =
                prodEnvironment("jdbc:postgresql://db.example.com:5432/financetracker", "owner@ft.example", "a-long-enough-password");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sslmode=verify-full");
    }

    @Test
    void shouldRefuseAnUrlWithAWeakerSslMode() {
        MockEnvironment environment =
                prodEnvironment("jdbc:postgresql://db.example.com:5432/financetracker?sslmode=require", "owner@ft.example", "a-long-enough-password");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("sslmode=verify-full");
    }

    @Test
    void shouldRefuseABlankOwnerEmail() {
        MockEnvironment environment = prodEnvironment(VALID_URL, "", "a-long-enough-password");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FT_OWNER_EMAIL");
    }

    @Test
    void shouldRefuseAMissingOwnerPassword() {
        MockEnvironment environment = prodEnvironment(VALID_URL, "owner@ft.example", null);

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("FT_OWNER_PASSWORD");
    }

    @Test
    void shouldFailRealContextRefreshUnderProdWithBadConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.url=jdbc:postgresql://db.example.com:5432/financetracker",
                        "ft.owner.email=owner@ft.example",
                        "ft.owner.password=a-long-enough-password")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldNotFailRealContextRefreshUnderProdWithGoodConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.url=" + VALID_URL,
                        "ft.owner.email=owner@ft.example",
                        "ft.owner.password=a-long-enough-password")
                .run(context -> assertThat(context).hasNotFailed());
    }

    private static MockEnvironment prodEnvironment(String datasourceUrl, String ownerEmail, String ownerPassword) {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(ProductionEnvironmentGuard.PROD_PROFILE);
        if (datasourceUrl != null) {
            environment.setProperty("spring.datasource.url", datasourceUrl);
        }
        if (ownerEmail != null) {
            environment.setProperty("ft.owner.email", ownerEmail);
        }
        if (ownerPassword != null) {
            environment.setProperty("ft.owner.password", ownerPassword);
        }
        return environment;
    }
}
