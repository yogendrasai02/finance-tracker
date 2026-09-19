package com.financetracker.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.PropertySource;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.env.MockEnvironment;

/** No Spring context for the branch-level cases below, and no Docker anywhere — every case here runs against a plain {@link MockEnvironment} or a minimal {@link ApplicationContextRunner}. */
class ProductionEnvironmentGuardTest {

    private static final String VALID_URL = "jdbc:postgresql://db.example.com:5432/financetracker?sslmode=verify-full";

    private static final String DETAIL_PROPERTY = "spring.datasource.hikari.data-source-properties.logServerErrorDetail";

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

    /** Proves prod can actually start with the files that ship, so the guard is not only right in isolation. */
    @Test
    void shouldPassTheShippedBaseAndProdConfiguration() throws IOException {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(ProductionEnvironmentGuard.PROD_PROFILE);
        environment.getPropertySources().addLast(loadYaml("application-prod.yml"));
        environment.getPropertySources().addLast(loadYaml("application.yml"));
        environment.setProperty("DB_URL", VALID_URL);
        environment.setProperty("FT_OWNER_EMAIL", "owner@ft.example");
        environment.setProperty("FT_OWNER_PASSWORD", "a-long-enough-password");

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
    void shouldRefuseServerErrorDetailSetToTrue() {
        MockEnvironment environment = prodEnvironment(VALID_URL, "owner@ft.example", "a-long-enough-password");
        environment.setProperty(DETAIL_PROPERTY, "true");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logServerErrorDetail");
    }

    /** pgjdbc's own default is true, so leaving the property out is as unsafe as setting it. */
    @Test
    void shouldRefuseServerErrorDetailLeftUnset() {
        MockEnvironment environment = prodEnvironmentWithoutLoggingSafety();

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logServerErrorDetail");
    }

    /** A URL parameter overrides the driver property in pgjdbc, so a safe property does not help if the URL turns detail back on. */
    @Test
    void shouldRefuseServerErrorDetailTurnedOnInTheUrl() {
        MockEnvironment environment =
                prodEnvironment(VALID_URL + "&logServerErrorDetail=true", "owner@ft.example", "a-long-enough-password");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("logServerErrorDetail");
    }

    @Test
    void shouldPassServerErrorDetailTurnedOffInTheUrlToo() {
        MockEnvironment environment =
                prodEnvironment(VALID_URL + "&logServerErrorDetail=false", "owner@ft.example", "a-long-enough-password");

        guard.validate(environment);
    }

    @ParameterizedTest
    @ValueSource(strings = {"org.springframework.security.authentication", "org.hibernate.orm.jdbc.bind"})
    void shouldRefuseDebugOnAPinnedLogger(String logger) {
        MockEnvironment environment = prodEnvironment(VALID_URL, "owner@ft.example", "a-long-enough-password");
        environment.setProperty("logging.level." + logger, "DEBUG");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining(logger);
    }

    @Test
    void shouldRefuseTraceOnALoggerBelowAPinnedOne() {
        MockEnvironment environment = prodEnvironment(VALID_URL, "owner@ft.example", "a-long-enough-password");
        environment.setProperty("logging.level.org.springframework.security.authentication.dao", "TRACE");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("org.springframework.security.authentication");
    }

    @Test
    void shouldRefuseADebugRootWhenThePinIsMissing() {
        MockEnvironment environment = prodEnvironmentWithoutLoggingSafety();
        environment.setProperty(DETAIL_PROPERTY, "false");
        environment.setProperty("logging.level.root", "DEBUG");

        assertThatThrownBy(() -> guard.validate(environment))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("must not run at DEBUG or TRACE");
    }

    @Test
    void shouldPassADebugRootWhileThePinsHold() {
        MockEnvironment environment = prodEnvironment(VALID_URL, "owner@ft.example", "a-long-enough-password");
        environment.setProperty("logging.level.root", "DEBUG");

        guard.validate(environment);
    }

    @Test
    void shouldFailRealContextRefreshUnderProdWithBadConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.url=jdbc:postgresql://db.example.com:5432/financetracker",
                        "ft.owner.email=owner@ft.example",
                        "ft.owner.password=a-long-enough-password",
                        DETAIL_PROPERTY + "=false")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void shouldNotFailRealContextRefreshUnderProdWithGoodConfiguration() {
        contextRunner
                .withPropertyValues(
                        "spring.profiles.active=prod",
                        "spring.datasource.url=" + VALID_URL,
                        "ft.owner.email=owner@ft.example",
                        "ft.owner.password=a-long-enough-password",
                        DETAIL_PROPERTY + "=false")
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
        environment.setProperty(DETAIL_PROPERTY, "false");
        environment.setProperty("logging.level.org.springframework.security.authentication", "INFO");
        environment.setProperty("logging.level.org.hibernate.orm.jdbc.bind", "INFO");
        return environment;
    }

    /** Valid on every earlier check, with neither the detail property nor the logger pins set. */
    private static MockEnvironment prodEnvironmentWithoutLoggingSafety() {
        MockEnvironment environment = new MockEnvironment();
        environment.setActiveProfiles(ProductionEnvironmentGuard.PROD_PROFILE);
        environment.setProperty("spring.datasource.url", VALID_URL);
        environment.setProperty("ft.owner.email", "owner@ft.example");
        environment.setProperty("ft.owner.password", "a-long-enough-password");
        return environment;
    }

    private static PropertySource<?> loadYaml(String name) throws IOException {
        return new YamlPropertySourceLoader().load(name, new ClassPathResource(name)).getFirst();
    }
}
