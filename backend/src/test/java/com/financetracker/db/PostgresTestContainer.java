package com.financetracker.db;

import java.nio.file.Files;
import java.nio.file.Path;

import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * One Postgres container, started on first use and shared by every schema test in the run.
 *
 * Starting Postgres takes a few seconds, so doing it once per test class is so slow that its bad for CI/CD and for DX.
 * 
 * Nothing closes this container on purpose: Testcontainer's reaper stops it when the JVM exits.
 */
final class PostgresTestContainer {

    static final String DATABASE = "financetracker";
    static final String MIGRATOR_USER = "ft_migrator";
    static final String MIGRATOR_PASSWORD = "ft_migrator_test";
    static final String APP_USER = "ft_app";
    static final String APP_PASSWORD = "ft_app_test";

    // Maven runs with the backend module as the working directory.
    private static final Path INIT_SCRIPT = Path.of("..", "db", "init", "01-roles-and-schema.sh");

    private static final PostgreSQLContainer CONTAINER = start();

    private PostgresTestContainer() {
    }

    private static PostgreSQLContainer start() {
        Path script = INIT_SCRIPT.toAbsolutePath().normalize();
        if (!Files.isRegularFile(script)) {
            throw new IllegalStateException("Role setup script not found at " + script + ". Run the tests from the backend module.");
        }

        PostgreSQLContainer container = new PostgreSQLContainer("postgres:18-alpine");
        container.withDatabaseName(DATABASE)
                // The same script local dev and CI use, so the roles cannot drift between
                // environments (DM-33).
                .withCopyFileToContainer(
                        MountableFile.forHostPath(script, 0755),
                        "/docker-entrypoint-initdb.d/01-roles-and-schema.sh")
                .withEnv("FT_MIGRATOR_PASSWORD", MIGRATOR_PASSWORD)
                .withEnv("FT_APP_PASSWORD", APP_PASSWORD);

        container.start();
        return container;
    }

    static String jdbcUrl() {
        return "jdbc:postgresql://%s:%d/%s?currentSchema=app"
                .formatted(CONTAINER.getHost(), CONTAINER.getFirstMappedPort(), DATABASE);
    }
}
