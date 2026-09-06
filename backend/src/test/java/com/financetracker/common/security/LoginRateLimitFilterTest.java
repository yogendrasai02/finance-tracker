package com.financetracker.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.financetracker.auth.OwnerCredentialBootstrap;
import com.financetracker.db.PostgresTestContainer;
import com.financetracker.testsupport.CookieJarHttpClient;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The login rate limit over real HTTP (SR-39, D-40).
 *
 * A key is client IP plus the submitted email.
 * Every request in this test comes from the same client, so only the email varies between cases.
 * Five attempts per minute is the configured default in {@code application.yml}, so the sixth in the same minute is the one that trips it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class LoginRateLimitFilterTest {

    private static final String PASSWORD = "not-a-real-password-1234";

    private static final String DISPLAY_NAME = "RateLimit";

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String ME = "/api/v1/me";

    private static final String CSRF_COOKIE = "XSRF-TOKEN";

    private static final String CSRF_HEADER = "X-XSRF-TOKEN";

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresTestContainer.APP_USER);
        registry.add("spring.datasource.password", () -> PostgresTestContainer.APP_PASSWORD);
        registry.add("spring.flyway.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.flyway.user", () -> PostgresTestContainer.MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> PostgresTestContainer.MIGRATOR_PASSWORD);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private OwnerCredentialBootstrap bootstrap;

    private String email;

    private CookieJarHttpClient browser;

    @BeforeEach
    void createUserWithACredential() throws Exception {
        email = createUser();
        bootstrap.bootstrap(email, PASSWORD);
        browser = new CookieJarHttpClient(port, CSRF_COOKIE, CSRF_HEADER);
        browser.get(ME);
    }

    @Test
    void shouldRejectTheSixthLoginAttemptWithinAMinuteForTheSameEmailAndClient() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            HttpResponse<String> response = browser.post(LOGIN, credentials(email, "wrong-password"));
            assertThat(response.statusCode()).as("attempt %d should still be evaluated normally", attempt).isEqualTo(401);
        }

        HttpResponse<String> sixth = browser.post(LOGIN, credentials(email, "wrong-password"));

        assertThat(sixth.statusCode()).isEqualTo(429);
        assertThat(sixth.headers().firstValue("Retry-After")).isPresent();
    }

    @Test
    void shouldRevealNothingAboutTheAccountInTheLimitedResponse() throws Exception {
        for (int attempt = 1; attempt <= 5; attempt++) {
            browser.post(LOGIN, credentials(email, "wrong-password"));
        }

        HttpResponse<String> limited = browser.post(LOGIN, credentials(email, "wrong-password"));

        assertThat(limited.body()).doesNotContain(email).doesNotContainIgnoringCase("credentials").doesNotContainIgnoringCase("password");
        assertThat(limited.body()).contains("Too many attempts");
    }

    @Test
    void shouldTrackADifferentEmailFromTheSameClientIndependently() throws Exception {
        String otherEmail = createUser();
        bootstrap.bootstrap(otherEmail, PASSWORD);

        for (int attempt = 1; attempt <= 5; attempt++) {
            browser.post(LOGIN, credentials(email, "wrong-password"));
        }
        HttpResponse<String> limited = browser.post(LOGIN, credentials(email, "wrong-password"));
        HttpResponse<String> stillAllowed = browser.post(LOGIN, credentials(otherEmail, "wrong-password"));

        assertThat(limited.statusCode()).isEqualTo(429);
        assertThat(stillAllowed.statusCode()).isEqualTo(401);
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    /** Creates a user directly, as the migrator, because the application can only ever see its own tenant's rows. */
    private static String createUser() throws SQLException {
        String address = "rate-limit-" + System.nanoTime() + "@example.invalid";
        try (Connection migrator = DriverManager.getConnection(
                        PostgresTestContainer.jdbcUrl(),
                        PostgresTestContainer.MIGRATOR_USER,
                        PostgresTestContainer.MIGRATOR_PASSWORD);
                PreparedStatement statement =
                        migrator.prepareStatement("INSERT INTO app.users (email, display_name) VALUES (?, ?)")) {
            statement.setString(1, address);
            statement.setString(2, DISPLAY_NAME);
            statement.execute();
        }
        return address;
    }
}
