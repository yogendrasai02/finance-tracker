package com.financetracker.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import com.financetracker.db.PostgresTestContainer;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jdbc.test.autoconfigure.JdbcTest;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Proves SR-28 through the datasource the application actually builds from application.yml, not a hand-made connection.
 * A unique violation's logged text must name the constraint, which is allowed in a log, and must not carry the conflicting value.
 *
 * Postgres itself leaves key values out of DETAIL when Row-Level Security applies to the table, which is every table in {@code app}.
 * So the domain-table case below would pass even without the driver setting.
 * The setting is proved on {@code auth.spring_session}, which has no RLS (D-34) and whose unique session id is Secret.
 * The last case is the control: the same violation on a plain connection with pgjdbc's default shows the value.
 */
@JdbcTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class ServerErrorDetailRedactionTest {

    private static final String SESSION_CONSTRAINT = "spring_session_ux_session_id";

    private static final String INSERT_SESSION = """
            INSERT INTO auth.spring_session
                (primary_id, session_id, creation_time, last_access_time, max_inactive_interval, expiry_time)
            VALUES (?, ?, 0, 0, 1800, 0)
            """;

    private static final String INSERT_ACCOUNT =
            "INSERT INTO app.accounts (user_id, name, type, dedup_method) VALUES (?, ?, 'ASSET', 'NONE')";

    @DynamicPropertySource
    static void containerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.datasource.username", () -> PostgresTestContainer.APP_USER);
        registry.add("spring.datasource.password", () -> PostgresTestContainer.APP_PASSWORD);
        registry.add("spring.flyway.url", PostgresTestContainer::jdbcUrl);
        registry.add("spring.flyway.user", () -> PostgresTestContainer.MIGRATOR_USER);
        registry.add("spring.flyway.password", () -> PostgresTestContainer.MIGRATOR_PASSWORD);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void shouldNameTheConstraintButNotTheSessionIdOnTheApplicationDatasource() {
        String sessionId = UUID.randomUUID().toString();
        jdbcTemplate.update(INSERT_SESSION, UUID.randomUUID().toString(), sessionId);

        Throwable thrown = catchThrowable(() -> jdbcTemplate.update(INSERT_SESSION, UUID.randomUUID().toString(), sessionId));

        assertThat(thrown).isInstanceOf(DuplicateKeyException.class);
        assertThat(asLogged(thrown)).contains(SESSION_CONSTRAINT).doesNotContain(sessionId);
    }

    @Test
    void shouldNameTheConstraintButNotTheValueOnADomainTable() throws SQLException {
        long userId = insertUserAsMigrator();
        String accountName = "Redaction marker " + System.nanoTime();
        jdbcTemplate.queryForObject("SELECT set_config('app.user_id', ?, true)", String.class, Long.toString(userId));
        jdbcTemplate.update(INSERT_ACCOUNT, userId, accountName);

        Throwable thrown = catchThrowable(() -> jdbcTemplate.update(INSERT_ACCOUNT, userId, accountName));

        assertThat(thrown).isInstanceOf(DuplicateKeyException.class);
        assertThat(asLogged(thrown)).contains("ux_accounts_user_name").doesNotContain(accountName);
    }

    @Test
    void shouldShowTheSessionIdWithPgjdbcsDefault() throws SQLException {
        String sessionId = UUID.randomUUID().toString();

        try (Connection connection = DriverManager.getConnection(
                PostgresTestContainer.jdbcUrl(), PostgresTestContainer.APP_USER, PostgresTestContainer.APP_PASSWORD)) {
            connection.setAutoCommit(false);
            insertSession(connection, sessionId);

            Throwable thrown = catchThrowable(() -> insertSession(connection, sessionId));
            connection.rollback();

            assertThat(thrown).isInstanceOf(SQLException.class);
            assertThat(asLogged(thrown)).contains(SESSION_CONSTRAINT).contains(sessionId);
        }
    }

    private static void insertSession(Connection connection, String sessionId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(INSERT_SESSION)) {
            statement.setString(1, UUID.randomUUID().toString());
            statement.setString(2, sessionId);
            statement.executeUpdate();
        }
    }

    /** Committed as the migrator, which skips RLS, because the application connection cannot create a user. */
    private static long insertUserAsMigrator() throws SQLException {
        try (Connection migrator = DriverManager.getConnection(
                        PostgresTestContainer.jdbcUrl(), PostgresTestContainer.MIGRATOR_USER, PostgresTestContainer.MIGRATOR_PASSWORD);
                PreparedStatement statement = migrator.prepareStatement("INSERT INTO app.users (email) VALUES (?) RETURNING id")) {
            statement.setString(1, "redaction-" + System.nanoTime() + "@example.invalid");
            try (ResultSet resultSet = statement.executeQuery()) {
                resultSet.next();
                return resultSet.getLong(1);
            }
        }
    }

    /** The whole stack trace, with every cause's message, because that is what a logger writes for an exception. */
    private static String asLogged(Throwable thrown) {
        StringWriter writer = new StringWriter();
        thrown.printStackTrace(new PrintWriter(writer));
        return writer.toString();
    }
}
