package com.financetracker.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

import com.financetracker.auth.OwnerCredentialBootstrap;
import com.financetracker.db.PostgresTestContainer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves the session actually lives in auth.spring_session, not JVM memory: a real row appears on login, disappears on logout, and disappears when the session outlives the absolute limit (SR-38, STEP4_PLAN.md 4f).
 *
 * The absolute limit is overridden to two seconds for this test only, so the second case does not need to wait twelve real hours.
 * Overriding it is not a departure from production behaviour: AbsoluteSessionTimeoutFilter reads the same property either way.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SessionStoreTest {

    private static final String PASSWORD = "not-a-real-password-1234";

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
        registry.add("ft.session.absolute-timeout", () -> "2s");
    }

    @LocalServerPort
    private int port;

    @Autowired
    private OwnerCredentialBootstrap bootstrap;

    private String email;

    private Browser browser;

    @BeforeEach
    void createUserWithACredential() throws SQLException {
        email = createUser();
        bootstrap.bootstrap(email, PASSWORD);
        browser = new Browser();
    }

    @Test
    void shouldStoreASessionRowAfterLogin() throws Exception {
        browser.get(ME);
        browser.post(LOGIN, credentials(email, PASSWORD));

        assertThat(sessionRowCount(browser.cookie(SESSION_COOKIE))).isEqualTo(1);
    }

    @Test
    void shouldRemoveTheSessionRowOnLogout() throws Exception {
        browser.get(ME);
        browser.post(LOGIN, credentials(email, PASSWORD));
        String sessionId = browser.cookie(SESSION_COOKIE);

        browser.post(LOGOUT, "");

        assertThat(sessionRowCount(sessionId)).isZero();
    }

    @Test
    void shouldInvalidateAndRemoveASessionOlderThanTheAbsoluteLimit() throws Exception {
        browser.get(ME);
        browser.post(LOGIN, credentials(email, PASSWORD));
        String sessionId = browser.cookie(SESSION_COOKIE);

        Thread.sleep(2500);
        HttpResponse<String> response = browser.get(ME);

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(sessionRowCount(sessionId)).as("the expired session's row was deleted, not merely ignored").isZero();
    }

    /**
     * The cookie value is base64 of the session id, not the id itself: {@code DefaultCookieSerializer} encodes it that way by default, so the table's {@code session_id} column has to be compared against the decoded form.
     */
    private static long sessionRowCount(String cookieValue) throws SQLException {
        String sessionId = new String(Base64.getDecoder().decode(cookieValue), StandardCharsets.UTF_8);
        try (Connection connection = DriverManager.getConnection(
                        PostgresTestContainer.jdbcUrl(),
                        PostgresTestContainer.MIGRATOR_USER,
                        PostgresTestContainer.MIGRATOR_PASSWORD);
                PreparedStatement statement =
                        connection.prepareStatement("SELECT count(*) FROM auth.spring_session WHERE session_id = ?")) {
            statement.setString(1, sessionId);
            var rows = statement.executeQuery();
            rows.next();
            return rows.getLong(1);
        }
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    private static String createUser() throws SQLException {
        String address = "session-" + System.nanoTime() + "@example.invalid";
        try (Connection migrator = DriverManager.getConnection(
                        PostgresTestContainer.jdbcUrl(),
                        PostgresTestContainer.MIGRATOR_USER,
                        PostgresTestContainer.MIGRATOR_PASSWORD);
                PreparedStatement statement =
                        migrator.prepareStatement("INSERT INTO app.users (email, display_name) VALUES (?, ?)")) {
            statement.setString(1, address);
            statement.setString(2, "Session Test");
            statement.execute();
        }
        return address;
    }

    /** A cookie jar and nothing else, copied from AuthenticationIntegrationTest: the assertions here are about the stored row, not the cookie attributes, but posting a login still needs the CSRF dance. */
    private final class Browser {

        private final HttpClient client = HttpClient.newHttpClient();

        private final Map<String, String> cookies = new LinkedHashMap<>();

        HttpResponse<String> get(String path) throws IOException, InterruptedException {
            return send(request(path).GET());
        }

        HttpResponse<String> post(String path, String body) throws IOException, InterruptedException {
            HttpRequest.Builder request = request(path)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            String token = cookies.get(CSRF_COOKIE);
            if (token != null) {
                request.header(CSRF_HEADER, token);
            }
            return send(request);
        }

        String cookie(String name) {
            return cookies.get(name);
        }

        private HttpRequest.Builder request(String path) {
            HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
            if (!cookies.isEmpty()) {
                builder.header("Cookie", cookies.entrySet().stream()
                        .map(entry -> entry.getKey() + "=" + entry.getValue())
                        .reduce((first, second) -> first + "; " + second)
                        .orElseThrow());
            }
            return builder;
        }

        private HttpResponse<String> send(HttpRequest.Builder request) throws IOException, InterruptedException {
            HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
            response.headers().allValues("set-cookie").forEach(this::store);
            return response;
        }

        private void store(String setCookie) {
            String pair = setCookie.split(";", 2)[0];
            int separator = pair.indexOf('=');
            if (separator < 0) {
                return;
            }
            String name = pair.substring(0, separator).trim();
            String value = pair.substring(separator + 1).trim();
            if (value.isEmpty() || setCookie.contains("Max-Age=0")) {
                cookies.remove(name);
            } else {
                cookies.put(name, value);
            }
        }
    }
}
