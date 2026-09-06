package com.financetracker.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

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
 * The login flow over real HTTP, against a real servlet container.
 *
 * A mock request cannot check any of this.
 * Cookie attributes are written by the container, the session id is changed by the container, and the CSRF token travels as a cookie and a header.
 * Every one of those is a detail that can be wrong while the application still logs in successfully, which is the failure mode this whole area is prone to.
 *
 * Each test creates its own user and gives it a credential.
 * The seeded owner is left alone, because another test asserts its password hash is still null, and that assertion is the guard that no migration ever writes one.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthenticationIntegrationTest {

    private static final String PASSWORD = "not-a-real-password-1234";

    private static final String DISPLAY_NAME = "Integration";

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String LOGOUT = "/api/v1/auth/logout";

    private static final String ME = "/api/v1/me";

    private static final String SESSION_COOKIE = "FT_SESSION";

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
    void createUserWithACredential() throws SQLException {
        email = createUser();
        bootstrap.bootstrap(email, PASSWORD);
        browser = new CookieJarHttpClient(port, CSRF_COOKIE, CSRF_HEADER);
    }

    @Test
    void shouldGiveAnUnauthenticatedCallerA401AndACsrfToken() throws Exception {
        HttpResponse<String> response = browser.get(ME);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("Authentication is required");
        assertThat(browser.cookie(CSRF_COOKIE))
                .as("the frontend has to be able to read a token before it can post anything, including the login")
                .isNotNull();
        assertThat(browser.cookie(SESSION_COOKIE))
                .as("an unauthenticated caller must not be able to create sessions, since each one becomes a stored row")
                .isNull();
    }

    @Test
    void shouldReturnTheProfileAndASessionCookieWhenTheCredentialsAreCorrect() throws Exception {
        browser.get(ME);

        HttpResponse<String> response = browser.post(LOGIN, credentials(email, PASSWORD));

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"email\":\"" + email + "\"");
        assertThat(response.body()).contains("\"displayName\":\"" + DISPLAY_NAME + "\"");
        assertThat(response.body()).contains("\"userId\":");
        assertThat(browser.cookie(SESSION_COOKIE)).isNotNull();

        String setCookie = browser.setCookieHeader(response, SESSION_COOKIE);
        assertThat(setCookie).contains("HttpOnly").contains("Secure").contains("SameSite=Strict");
    }

    /**
     * The CSRF token is replaced on login for the same reason the session id is, and the replacement has to reach the browser.
     * A login that clears the token without issuing a new one still logs in, and then refuses the next thing the user does.
     */
    @Test
    void shouldIssueAFreshCsrfTokenOnLogin() throws Exception {
        browser.get(ME);
        String beforeLogin = browser.cookie(CSRF_COOKIE);

        HttpResponse<String> response = browser.post(LOGIN, credentials(email, PASSWORD));

        assertThat(browser.cookie(CSRF_COOKIE)).isNotNull().isNotEqualTo(beforeLogin);
        assertThat(browser.setCookieHeader(response, CSRF_COOKIE))
                .contains("Secure")
                .contains("SameSite=Strict")
                .as("the frontend has to read this one, so it is deliberately not HttpOnly")
                .doesNotContain("HttpOnly");
    }

    @Test
    void shouldReturnTheSameAnswerForAWrongPasswordAndForAnUnknownEmail() throws Exception {
        browser.get(ME);

        HttpResponse<String> wrongPassword = browser.post(LOGIN, credentials(email, "wrong-password-entirely"));
        HttpResponse<String> unknownEmail = browser.post(LOGIN, credentials("nobody@example.invalid", PASSWORD));

        assertThat(wrongPassword.statusCode()).isEqualTo(401);
        assertThat(unknownEmail.statusCode()).isEqualTo(wrongPassword.statusCode());
        assertThat(unknownEmail.body()).isEqualTo(wrongPassword.body());
    }

    /** An account that exists but is still waiting for its credential must be indistinguishable from one that does not exist. */
    @Test
    void shouldReturnTheSameAnswerForAnAccountWithNoCredential() throws Exception {
        String withoutCredential = createUser();
        browser.get(ME);

        HttpResponse<String> noCredential = browser.post(LOGIN, credentials(withoutCredential, PASSWORD));
        HttpResponse<String> unknownEmail = browser.post(LOGIN, credentials("nobody@example.invalid", PASSWORD));

        assertThat(noCredential.statusCode()).isEqualTo(401);
        assertThat(noCredential.body()).isEqualTo(unknownEmail.body());
    }

    @Test
    void shouldRejectAStateChangingRequestThatCarriesNoCsrfToken() throws Exception {
        browser.get(ME);

        HttpResponse<String> response = browser.postWithoutCsrfToken(LOGIN, credentials(email, PASSWORD));

        assertThat(response.statusCode()).isEqualTo(403);
    }

    @Test
    void shouldReturnTheProfileFromMeWhenTheSessionCookieIsSent() throws Exception {
        browser.get(ME);
        browser.post(LOGIN, credentials(email, PASSWORD));

        HttpResponse<String> response = browser.get(ME);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"email\":\"" + email + "\"");
    }

    /**
     * Session fixation protection.
     * Logging in while holding a session must produce a different session id, so an id planted in the browser before login is worthless after it.
     */
    @Test
    void shouldChangeTheSessionIdOnEveryLogin() throws Exception {
        browser.get(ME);
        browser.post(LOGIN, credentials(email, PASSWORD));
        String firstSession = browser.cookie(SESSION_COOKIE);

        browser.post(LOGIN, credentials(email, PASSWORD));
        String secondSession = browser.cookie(SESSION_COOKIE);

        assertThat(firstSession).isNotNull();
        assertThat(secondSession).isNotNull().isNotEqualTo(firstSession);
    }

    @Test
    void shouldRefuseTheSessionAfterLoggingOut() throws Exception {
        browser.get(ME);
        browser.post(LOGIN, credentials(email, PASSWORD));

        HttpResponse<String> logout = browser.post(LOGOUT, "");

        assertThat(logout.statusCode()).isEqualTo(204);
        assertThat(browser.get(ME).statusCode()).isEqualTo(401);
    }

    @Test
    void shouldSendTheSecurityHeadersOnEveryResponse() throws Exception {
        HttpResponse<String> response = browser.get(ME);

        assertThat(header(response, "X-Content-Type-Options")).isEqualTo("nosniff");
        assertThat(header(response, "X-Frame-Options")).isEqualTo("DENY");
        assertThat(header(response, "Referrer-Policy")).isEqualTo("no-referrer");
        assertThat(header(response, "Content-Security-Policy")).contains("frame-ancestors 'none'");
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    private static String header(HttpResponse<String> response, String name) {
        return response.headers().firstValue(name).orElse(null);
    }

    /** Creates a user directly, as the migrator, because the application can only ever see its own tenant's rows. */
    private static String createUser() throws SQLException {
        String address = "auth-" + System.nanoTime() + "@example.invalid";
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
