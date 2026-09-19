package com.financetracker.common.config;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.EnvironmentAware;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * Refuses to start the {@code prod} profile with configuration that would otherwise run insecurely instead of failing (SECURITY.md SR-25, SR-28, SR-35, SR-36, SR-40).
 *
 * A {@link BeanFactoryPostProcessor} runs during bean-factory post-processing, before any singleton bean — including the datasource — is created.
 * That is what makes the failure a clear message about missing configuration, rather than a driver error after an insecure connection was already attempted.
 */
@Component
public class ProductionEnvironmentGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    static final String PROD_PROFILE = "prod";

    static final String REQUIRED_SSL_MODE = "sslmode=verify-full";

    static final String SERVER_ERROR_DETAIL = "logServerErrorDetail";

    static final String HIKARI_DATA_SOURCE_PROPERTIES = "spring.datasource.hikari.data-source-properties";

    /** Loggers that write Restricted or Confidential values at DEBUG or TRACE, so prod must never run them that verbosely. */
    static final List<String> PINNED_LOGGERS = List.of("org.springframework.security.authentication", "org.hibernate.orm.jdbc.bind");

    private static final Bindable<Map<String, String>> STRING_MAP = Bindable.mapOf(String.class, String.class);

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
        requireServerErrorDetailOff(environment, jdbcUrl);
        requireNoVerbosePinnedLogger(environment);
    }

    private static void requireConfigured(Environment environment, String property, String envVarName) {
        if (!StringUtils.hasText(environment.getProperty(property))) {
            throw new IllegalStateException(
                    envVarName + " must be set under the prod profile (SECURITY.md SR-35, SR-40)");
        }
    }

    /**
     * Requires an explicit {@code false}, not just the absence of {@code true}, because pgjdbc's own default is {@code true}.
     * The URL is checked too, because a URL parameter overrides the driver property.
     */
    private static void requireServerErrorDetailOff(Environment environment, String jdbcUrl) {
        Map<String, String> driverProperties = Binder.get(environment).bind(HIKARI_DATA_SOURCE_PROPERTIES, STRING_MAP).orElse(Map.of());
        String configured = driverProperties.entrySet().stream()
                .filter(entry -> entry.getKey().equalsIgnoreCase(SERVER_ERROR_DETAIL))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(null);
        String fromUrl = urlParameter(jdbcUrl, SERVER_ERROR_DETAIL);

        if (!"false".equalsIgnoreCase(configured) || (fromUrl != null && !"false".equalsIgnoreCase(fromUrl))) {
            throw new IllegalStateException(
                    SERVER_ERROR_DETAIL + " must be false under the prod profile, in both the datasource properties and DB_URL (SECURITY.md SR-28)");
        }
    }

    /**
     * Checks each pinned logger's effective level, which is the level of its closest configured ancestor, and every configured logger below it.
     * So {@code root: DEBUG} with the pin removed, and a DEBUG setting on a child package, are both refused.
     */
    private static void requireNoVerbosePinnedLogger(Environment environment) {
        Map<String, String> levels = Binder.get(environment).bind("logging.level", STRING_MAP).orElse(Map.of());
        for (String pinned : PINNED_LOGGERS) {
            boolean verbose = isVerbose(effectiveLevel(levels, pinned))
                    || levels.entrySet().stream()
                            .anyMatch(entry -> entry.getKey().startsWith(pinned + ".") && isVerbose(entry.getValue()));
            if (verbose) {
                throw new IllegalStateException(
                        "Logger " + pinned + " must not run at DEBUG or TRACE under the prod profile (SECURITY.md SR-25)");
            }
        }
    }

    private static String effectiveLevel(Map<String, String> levels, String logger) {
        String name = logger;
        while (true) {
            String level = levels.get(name);
            if (level != null) {
                return level;
            }
            int lastDot = name.lastIndexOf('.');
            if (lastDot < 0) {
                return levels.get("root");
            }
            name = name.substring(0, lastDot);
        }
    }

    private static boolean isVerbose(String level) {
        return "DEBUG".equalsIgnoreCase(level) || "TRACE".equalsIgnoreCase(level);
    }

    private static String urlParameter(String jdbcUrl, String name) {
        int queryStart = jdbcUrl.indexOf('?');
        if (queryStart < 0) {
            return null;
        }
        for (String pair : jdbcUrl.substring(queryStart + 1).split("&")) {
            int equals = pair.indexOf('=');
            String key = equals < 0 ? pair : pair.substring(0, equals);
            if (key.equalsIgnoreCase(name)) {
                return equals < 0 ? "" : pair.substring(equals + 1);
            }
        }
        return null;
    }
}
