package com.financetracker.db;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.util.ArrayList;
import java.util.List;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;

/**
 * Base class for the schema tests.
 *
 * The tests open their own JDBC connections and name the role, because a test that runs as the schema owner passes while RLS is completely broken — an owner skips its own policies (DM-32). 
 * 
 * Every connection runs inside a transaction that is rolled back after the test, so the shared container needs no cleanup between tests.
 */
abstract class SchemaTestBase {

    private static boolean migrated;

    private final List<Connection> connections = new ArrayList<>();

    @BeforeAll
    static synchronized void migrateOnce() {
        if (migrated) {
            return;
        }
        Flyway.configure()
                .dataSource(
                        PostgresTestContainer.jdbcUrl(),
                        PostgresTestContainer.MIGRATOR_USER,
                        PostgresTestContainer.MIGRATOR_PASSWORD)
                .schemas("app")
                .defaultSchema("app")
                .createSchemas(false)
                .locations("classpath:db/migration")
                .load()
                .migrate();
        migrated = true;
    }

    /** A connection as the application role: DML only, fully subject to RLS. */
    protected Connection appConnection() throws SQLException {
        return open(PostgresTestContainer.APP_USER, PostgresTestContainer.APP_PASSWORD);
    }

    /** A connection as the schema owner. Used to set up rows belonging to any user. */
    protected Connection migratorConnection() throws SQLException {
        return open(PostgresTestContainer.MIGRATOR_USER, PostgresTestContainer.MIGRATOR_PASSWORD);
    }

    /**
     * Sets the tenant for the current transaction, the same way the application will.
     * The value is passed as a parameter rather than inlined, so a test can pass a malformed one.
     */
    protected void setUserId(Connection connection, String userId) throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement("SELECT set_config('app.user_id', ?, true)")) {
            statement.setString(1, userId);
            statement.execute();
        }
    }

    protected void setUserId(Connection connection, long userId) throws SQLException {
        setUserId(connection, String.valueOf(userId));
    }

    /**
     * Runs work that is expected to be refused and returns the error, so the caller can assert on
     * the constraint name. Postgres refuses every later statement in a transaction once one has
     * failed, so the work runs inside a savepoint the failure is rolled back to.
     */
    protected SQLException expectFailure(Connection connection, SqlWork work) throws SQLException {
        Savepoint savepoint = connection.setSavepoint();
        try {
            work.run(connection);
        } catch (SQLException refused) {
            connection.rollback(savepoint);
            return refused;
        }
        connection.rollback(savepoint);
        throw new AssertionError("Expected the statement to be refused, but it succeeded.");
    }

    protected SQLException expectFailure(Connection connection, String sql, Object... parameters)
            throws SQLException {
        return expectFailure(connection, c -> execute(c, sql, parameters));
    }

    protected static void execute(Connection connection, String sql, Object... parameters)
            throws SQLException {
        try (PreparedStatement statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < parameters.length; i++) {
                statement.setObject(i + 1, parameters[i]);
            }
            statement.execute();
        }
    }

    @AfterEach
    void rollbackAndClose() {
        for (Connection connection : connections) {
            try (connection) {
                connection.rollback();
            } catch (SQLException ignored) {
                // The test has already finished; a connection that cannot be rolled back is closed anyway.
            }
        }
        connections.clear();
    }

    private Connection open(String user, String password) throws SQLException {
        Connection connection = DriverManager.getConnection(PostgresTestContainer.jdbcUrl(), user, password);
        connection.setAutoCommit(false);
        connections.add(connection);
        return connection;
    }

    @FunctionalInterface
    protected interface SqlWork {
        void run(Connection connection) throws SQLException;
    }
}
