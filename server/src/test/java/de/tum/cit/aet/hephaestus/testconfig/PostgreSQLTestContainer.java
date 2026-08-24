package de.tum.cit.aet.hephaestus.testconfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.PostgreSQLContainer;

public final class PostgreSQLTestContainer {

    private static final String DEFAULT_TEST_DB = "hephaestus_test";
    private static final String DEFAULT_TEST_USER = "test";
    private static final String DEFAULT_TEST_PASSWORD = "test";

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgreSQLTestContainer.class);

    private static PostgreSQLContainer<?> container;

    private PostgreSQLTestContainer() {}

    public static synchronized PostgreSQLContainer<?> getInstance() {
        if (container == null) {
            container = createContainer();
        }
        return container;
    }

    @SuppressWarnings("resource") // Closed by the JVM shutdown hook.
    private static PostgreSQLContainer<?> createContainer() {
        PostgreSQLContainer<?> newContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName(DEFAULT_TEST_DB)
            .withUsername(DEFAULT_TEST_USER)
            .withPassword(DEFAULT_TEST_PASSWORD);

        newContainer.start();
        ensureExtensions(newContainer.getJdbcUrl(), newContainer.getUsername(), newContainer.getPassword());

        LOGGER.info(
            "Started PostgreSQL Testcontainer: jdbcUrl={}, username={}, database={}",
            newContainer.getJdbcUrl(),
            newContainer.getUsername(),
            newContainer.getDatabaseName()
        );

        Runtime.getRuntime().addShutdownHook(
            new Thread(() -> {
                if (newContainer.isRunning()) {
                    newContainer.stop();
                }
            })
        );

        return newContainer;
    }

    /** Enables extensions required by Hibernate-generated test schemas because Liquibase is disabled in tests. */
    private static void ensureExtensions(String jdbcUrl, String username, String password) {
        try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
            connection.createStatement().execute("CREATE EXTENSION IF NOT EXISTS citext");
        } catch (SQLException exception) {
            throw new IllegalStateException(
                "Failed to enable required PostgreSQL extension 'citext' on the test database: " +
                    exception.getMessage(),
                exception
            );
        }
    }
}
