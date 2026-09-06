package com.financetracker.account;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;

import com.financetracker.auth.OwnerCredentialBootstrap;
import com.financetracker.db.PostgresTestContainer;
import com.financetracker.testsupport.CookieJarHttpClient;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * The first endpoint that reads domain data, driven over real HTTP so the whole chain in STEP4_PLAN.md §1.8 is exercised end to end:
 * cookie, {@code SecurityContext}, tenant context, {@code set_config}, Row-Level Security.
 *
 * Each test creates its own users and accounts directly as {@code ft_migrator}, the same way {@code AuthenticationIntegrationTest} does, rather than using the seeded owner.
 * The seeded owner is left alone because another test asserts its password hash is still null.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AccountsIntegrationTest {

    private static final String PASSWORD = "not-a-real-password-1234";

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String ME = "/api/v1/me";

    private static final String ACCOUNTS = "/api/v1/accounts";

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

    @Test
    void shouldReturnTheCallersAccountsWhenAuthenticated() throws Exception {
        String email = createUserWithAccounts("HDFC Savings", "SBI Savings", "HDFC Millenia", "Investments");

        HttpResponse<String> response = loggedInAs(email).get(ACCOUNTS);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("HDFC Savings")
                .contains("SBI Savings")
                .contains("HDFC Millenia")
                .contains("Investments");
        assertThat(countOccurrences(response.body(), "\"id\":")).isEqualTo(4);
    }

    @Test
    void shouldReturn401WhenNotAuthenticated() throws Exception {
        CookieJarHttpClient browser = new CookieJarHttpClient(port, CSRF_COOKIE, CSRF_HEADER);

        HttpResponse<String> response = browser.get(ACCOUNTS);

        assertThat(response.statusCode()).isEqualTo(401);
    }

    /** Two tenants, same running application, same connection pool: proves RLS separates them rather than only the seed data happening to look separate. */
    @Test
    void shouldReturnOnlyTheCallersOwnAccountsWhenTwoUsersExist() throws Exception {
        String userA = createUserWithAccounts("Groceries Wallet", "Salary Account");
        String userB = createUserWithAccounts("Rent Wallet");

        HttpResponse<String> responseA = loggedInAs(userA).get(ACCOUNTS);
        assertThat(responseA.body()).contains("Groceries Wallet").contains("Salary Account").doesNotContain("Rent Wallet");

        HttpResponse<String> responseB = loggedInAs(userB).get(ACCOUNTS);
        assertThat(responseB.body())
                .contains("Rent Wallet")
                .doesNotContain("Groceries Wallet")
                .doesNotContain("Salary Account");
    }

    private CookieJarHttpClient loggedInAs(String email) throws Exception {
        CookieJarHttpClient browser = new CookieJarHttpClient(port, CSRF_COOKIE, CSRF_HEADER);
        browser.get(ME);
        HttpResponse<String> login = browser.post(LOGIN, credentials(email, PASSWORD));
        assertThat(login.statusCode()).as("test setup: login must succeed before the assertion under test").isEqualTo(200);
        return browser;
    }

    private static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    private static int countOccurrences(String body, String needle) {
        return (body.length() - body.replace(needle, "").length()) / needle.length();
    }

    /** Creates a user and its accounts directly as the migrator, then gives the user a credential through the same bootstrap the application uses on startup. */
    private String createUserWithAccounts(String... accountNames) throws SQLException {
        String email = "accounts-" + System.nanoTime() + "@example.invalid";

        try (Connection migrator = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(), PostgresTestContainer.MIGRATOR_USER, PostgresTestContainer.MIGRATOR_PASSWORD)) {
            long userId;
            try (PreparedStatement insertUser = migrator.prepareStatement(
                    "INSERT INTO app.users (email, display_name) VALUES (?, 'Accounts Test') RETURNING id")) {
                insertUser.setString(1, email);
                var rows = insertUser.executeQuery();
                rows.next();
                userId = rows.getLong("id");
            }
            try (PreparedStatement insertAccount = migrator.prepareStatement(
                    "INSERT INTO app.accounts (user_id, name, type, dedup_method) VALUES (?, ?, 'ASSET', 'NONE')")) {
                for (String accountName : accountNames) {
                    insertAccount.setLong(1, userId);
                    insertAccount.setString(2, accountName);
                    insertAccount.addBatch();
                }
                insertAccount.executeBatch();
            }
        }

        bootstrap.bootstrap(email, PASSWORD);
        return email;
    }
}
