package com.financetracker.testsupport;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.http.HttpResponse;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.HashMap;
import java.util.Map;

import com.financetracker.auth.OwnerCredentialBootstrap;
import com.financetracker.db.PostgresTestContainer;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Reusable base for integration tests that need two isolated tenants.
 *
 * Seeds two users, A and B, each with an account and provides helpers to obtain a logged-in session for either.
 * Every IDOR test extends this class rather than repeating the setup.
 *
 * Subclasses get the seeded email addresses and database ids, one account id per user, a shared session for each user, a fresh-session helper, and an anonymous cookie jar.
 *
 * The seeded owner is never touched; another test guards that its password hash stays null.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public abstract class TwoUserTestHarness {

    protected static final String PASSWORD = "not-a-real-password-1234";

    private static final String LOGIN = "/api/v1/auth/login";

    private static final String ME = "/api/v1/me";

    protected static final String CSRF_COOKIE = "XSRF-TOKEN";

    protected static final String CSRF_HEADER = "X-XSRF-TOKEN";

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
    protected int port;

    /**
     * The application's own credential bootstrap, reused here to set the seeded users' passwords.
     *
     * This is deliberate rather than convenient: it is the only sanctioned write to password_hash (D-36).
     * A test that hashed a password by hand would be testing a path the application never takes.
     */
    @Autowired
    private OwnerCredentialBootstrap bootstrap;

    /** One session per user, reused across the test class. See loggedInAs. */
    private final Map<String, CookieJarHttpClient> sessions = new HashMap<>();

    /** Set once during seeding, then stable for the rest of the test class. */
    protected String userAEmail;

    protected String userBEmail;

    protected long userAId;

    protected long userBId;

    protected long userAAccountId;

    protected long userBAccountId;

    /**
     * Seeds two users with accounts and credentials, once per test class.
     *
     * The class runs PER_CLASS so this can be a non-static @BeforeAll method that sees the injected Spring fields.
     * Seeding per test method instead would create a fresh pair of users for every method, which is slow and pointless: no test here mutates a user.
     */
    @BeforeAll
    protected void seedTwoUsers() throws SQLException {
        userAEmail = "idor-a-" + System.nanoTime() + "@example.invalid";
        userBEmail = "idor-b-" + System.nanoTime() + "@example.invalid";

        try (Connection migrator = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(), PostgresTestContainer.MIGRATOR_USER, PostgresTestContainer.MIGRATOR_PASSWORD)) {
            userAId = insertUserWithAccount(migrator, userAEmail, "User A", "A Savings");
            userAAccountId = lookupAccountId(migrator, userAId);

            userBId = insertUserWithAccount(migrator, userBEmail, "User B", "B Savings");
            userBAccountId = lookupAccountId(migrator, userBId);
        }

        bootstrap.bootstrap(userAEmail, PASSWORD);
        bootstrap.bootstrap(userBEmail, PASSWORD);
    }

    /**
     * Returns the test class's session for the given user and logs in on first use.
     *
     * The session is shared by every test in the class on purpose.
     * Logging in per test would spend one attempt of the login rate limit each time, and that limit is 5 per minute for one email from one client (D-40).
     * A class with six IDOR cases would then start failing with 429 for a reason unrelated to the test.
     * A test that needs its own session or logs out must use freshLoginAs.
     *
     * @param email the user email
     * @return the shared session for the user
     * @throws Exception if login fails
     */
    protected CookieJarHttpClient loggedInAs(String email) throws Exception {
        CookieJarHttpClient existing = sessions.get(email);
        if (existing != null) {
            return existing;
        }
        CookieJarHttpClient browser = freshLoginAs(email);
        sessions.put(email, browser);
        return browser;
    }

    /**
     * Logs in again on a new cookie jar for a test that needs a session of its own.
     *
     * @param email the user email
     * @return a new authenticated session
     * @throws Exception if login fails
     */
    protected CookieJarHttpClient freshLoginAs(String email) throws Exception {
        CookieJarHttpClient browser = anonymousBrowser();
        browser.get(ME);
        HttpResponse<String> login = browser.post(LOGIN, credentials(email, PASSWORD));
        assertThat(login.statusCode())
                .as("test setup: login must succeed for %s before the assertion under test", email)
                .isEqualTo(200);
        return browser;
    }

    /** Returns a cookie jar with no session that picks up the CSRF token on first use. */
    protected CookieJarHttpClient anonymousBrowser() {
        return new CookieJarHttpClient(port, CSRF_COOKIE, CSRF_HEADER);
    }

    protected static String credentials(String email, String password) {
        return """
                {"email": "%s", "password": "%s"}""".formatted(email, password);
    }

    /** Inserts a user and one account, returning the user id. */
    private static long insertUserWithAccount(Connection migrator, String email, String displayName, String accountName)
            throws SQLException {
        long userId;
        try (PreparedStatement insertUser = migrator.prepareStatement(
                "INSERT INTO app.users (email, display_name) VALUES (?, ?) RETURNING id")) {
            insertUser.setString(1, email);
            insertUser.setString(2, displayName);
            var rows = insertUser.executeQuery();
            rows.next();
            userId = rows.getLong("id");
        }
        try (PreparedStatement insertAccount = migrator.prepareStatement(
                "INSERT INTO app.accounts (user_id, name, type, dedup_method) VALUES (?, ?, 'ASSET', 'NONE')")) {
            insertAccount.setLong(1, userId);
            insertAccount.setString(2, accountName);
            insertAccount.execute();
        }
        return userId;
    }

    private static long lookupAccountId(Connection migrator, long userId) throws SQLException {
        try (PreparedStatement ps = migrator.prepareStatement(
                "SELECT id FROM app.accounts WHERE user_id = ? LIMIT 1")) {
            ps.setLong(1, userId);
            var rows = ps.executeQuery();
            rows.next();
            return rows.getLong("id");
        }
    }
}
