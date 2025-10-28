package de.tum.in.www1.hephaestus.testconfig;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Locale;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton PostgreSQL container for integration tests.
 * Shared across all tests for performance improvement.
 */
public final class PostgreSQLTestContainer {

    private static final String ENV_DB_MODE = "HEPHAESTUS_DB_MODE";
    private static final String ENV_TEST_JDBC_URL = "HEPHAESTUS_TEST_JDBC_URL";
    private static final String ENV_TEST_DB_USER = "HEPHAESTUS_TEST_DB_USER";
    private static final String ENV_TEST_DB_PASSWORD = "HEPHAESTUS_TEST_DB_PASSWORD";
    private static final String DEFAULT_TEST_DB = "hephaestus_test";
    private static final String DEFAULT_TEST_USER = "test";
    private static final String DEFAULT_TEST_PASSWORD = "test";
    private static final String DEFAULT_TEST_JDBC_URL =
        "jdbc:postgresql://localhost:5432/" + DEFAULT_TEST_DB;

    private static final Logger LOGGER = LoggerFactory.getLogger(PostgreSQLTestContainer.class);

    private static PostgreSQLContainer<?> container;

    private PostgreSQLTestContainer() {
        // Utility class - prevent instantiation
    }

    /**
     * Gets the shared PostgreSQL container instance, starting it if necessary.
     *
     * @return the PostgreSQL container instance
     */
    public static PostgreSQLContainer<?> getInstance() {
        if (container == null) {
            synchronized (PostgreSQLTestContainer.class) {
                if (container == null) {
                    container = createContainer();
                }
            }
        }
        return container;
    }

    @SuppressWarnings("resource") // Container lifecycle is managed by shutdown hook
    private static PostgreSQLContainer<?> createContainer() {
        if (useLocalDatabase()) {
            return startLocalContainer();
        }

        if (!isDockerAvailable()) {
            LOGGER.warn("Docker is not available. Falling back to the locally managed PostgreSQL instance.");
            return startLocalContainer();
        }

        try {
            return startDockerContainer();
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to start PostgreSQL Testcontainer. Falling back to local instance.", exception);
            try {
                return startLocalContainer();
            } catch (RuntimeException fallbackException) {
                exception.addSuppressed(fallbackException);
                throw exception;
            }
        }
    }

    private static boolean useLocalDatabase() {
        String dbMode = Optional.ofNullable(System.getenv(ENV_DB_MODE)).map(v -> v.toLowerCase(Locale.ROOT)).orElse(null);
        return "local".equals(dbMode);
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> startDockerContainer() {
        PostgreSQLContainer<?> newContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName(DEFAULT_TEST_DB)
            .withUsername(DEFAULT_TEST_USER)
            .withPassword(DEFAULT_TEST_PASSWORD);

        newContainer.start();

        Runtime
            .getRuntime()
            .addShutdownHook(
                new Thread(() -> {
                    if (newContainer.isRunning()) {
                        newContainer.stop();
                    }
                })
            );

        return newContainer;
    }

    private static PostgreSQLContainer<?> startLocalContainer() {
        LocalPostgresContainer localContainer = new LocalPostgresContainer();
        localContainer.start();
        return localContainer;
    }

    private static boolean isDockerAvailable() {
        try {
            return DockerClientFactory.instance().isDockerAvailable();
        } catch (Throwable exception) {
            return false;
        }
    }

    private static final class LocalPostgresContainer extends PostgreSQLContainer<LocalPostgresContainer> {

        private final String jdbcUrl;
        private final String username;
        private final String password;
        private final String databaseName;

        private LocalPostgresContainer() {
            super("postgres:16");
            this.jdbcUrl = Optional.ofNullable(System.getenv(ENV_TEST_JDBC_URL)).orElse(DEFAULT_TEST_JDBC_URL);
            this.username = Optional.ofNullable(System.getenv(ENV_TEST_DB_USER)).orElse(DEFAULT_TEST_USER);
            this.password = Optional.ofNullable(System.getenv(ENV_TEST_DB_PASSWORD)).orElse(DEFAULT_TEST_PASSWORD);
            this.databaseName = extractDatabaseName(jdbcUrl).orElse(DEFAULT_TEST_DB);
        }

        @Override
        public void start() {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                connection.createStatement().execute("SELECT 1");
            } catch (SQLException exception) {
                throw new IllegalStateException(
                    "Failed to connect to local PostgreSQL instance at " + jdbcUrl +
                    ". Run 'run/setup.sh' and ensure scripts/local-postgres.sh start completes successfully before running tests.",
                    exception
                );
            }
        }

        @Override
        public void stop() {
            // Local database lifecycle is managed externally.
        }

        @Override
        public String getJdbcUrl() {
            return jdbcUrl;
        }

        @Override
        public String getUsername() {
            return username;
        }

        @Override
        public String getPassword() {
            return password;
        }

        @Override
        public String getDatabaseName() {
            return databaseName;
        }

        private static Optional<String> extractDatabaseName(String jdbcUrl) {
            int lastSlash = jdbcUrl.lastIndexOf('/');
            if (lastSlash < 0 || lastSlash + 1 >= jdbcUrl.length()) {
                return Optional.empty();
            }

            int questionMark = jdbcUrl.indexOf('?', lastSlash);
            if (questionMark == -1) {
                return Optional.of(jdbcUrl.substring(lastSlash + 1));
            }

            if (questionMark == lastSlash + 1) {
                return Optional.empty();
            }

            return Optional.of(jdbcUrl.substring(lastSlash + 1, questionMark));
        }
    }
}
