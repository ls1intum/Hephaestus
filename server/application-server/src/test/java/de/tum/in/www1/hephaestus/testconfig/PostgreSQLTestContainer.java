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
    private static final String DEFAULT_TEST_JDBC_URL = "jdbc:postgresql://localhost:5432/" + DEFAULT_TEST_DB;

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
            return startLocalContainer(LocalDatabaseReason.EXPLICIT_LOCAL_MODE);
        }

        if (!isDockerAvailable()) {
            LOGGER.warn("Docker is not available. Falling back to the locally managed PostgreSQL instance.");
            return startLocalContainer(LocalDatabaseReason.DOCKER_UNAVAILABLE);
        }

        try {
            return startDockerContainer();
        } catch (RuntimeException exception) {
            LOGGER.warn("Failed to start PostgreSQL Testcontainer. Falling back to local instance.", exception);
            try {
                return startLocalContainer(LocalDatabaseReason.DOCKER_START_FAILED);
            } catch (RuntimeException fallbackException) {
                exception.addSuppressed(fallbackException);
                throw exception;
            }
        }
    }

    private static boolean useLocalDatabase() {
        String dbMode = Optional.ofNullable(System.getenv(ENV_DB_MODE))
            .map(v -> v.toLowerCase(Locale.ROOT))
            .orElse(null);
        return "local".equals(dbMode);
    }

    @SuppressWarnings("resource")
    private static PostgreSQLContainer<?> startDockerContainer() {
        PostgreSQLContainer<?> newContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName(DEFAULT_TEST_DB)
            .withUsername(DEFAULT_TEST_USER)
            .withPassword(DEFAULT_TEST_PASSWORD);

        newContainer.start();

        LOGGER.info(
            "Started PostgreSQL Testcontainer via Docker: jdbcUrl={}, username={}, database={}",
            newContainer.getJdbcUrl(),
            newContainer.getUsername(),
            newContainer.getDatabaseName()
        );

        Runtime.getRuntime()
            .addShutdownHook(
                new Thread(() -> {
                    if (newContainer.isRunning()) {
                        newContainer.stop();
                    }
                })
            );

        return newContainer;
    }

    private static PostgreSQLContainer<?> startLocalContainer(LocalDatabaseReason reason) {
        LocalPostgresContainer localContainer = new LocalPostgresContainer(reason);
        localContainer.start();
        return localContainer;
    }
    
    /**
     * Enum representing the reason why local PostgreSQL database is being used.
     */
    private enum LocalDatabaseReason {
        EXPLICIT_LOCAL_MODE("HEPHAESTUS_DB_MODE is set to 'local', forcing local database mode."),
        DOCKER_UNAVAILABLE("Docker is not available."),
        DOCKER_START_FAILED("Docker is available but Testcontainer startup failed (fell back to local mode).");
        
        private final String description;
        
        LocalDatabaseReason(String description) {
            this.description = description;
        }
        
        public String getDescription() {
            return description;
        }
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
        private final LocalDatabaseReason reason;

        private LocalPostgresContainer(LocalDatabaseReason reason) {
            super("postgres:16");
            this.reason = reason;
            this.jdbcUrl = Optional.ofNullable(System.getenv(ENV_TEST_JDBC_URL)).orElse(DEFAULT_TEST_JDBC_URL);
            this.username = Optional.ofNullable(System.getenv(ENV_TEST_DB_USER)).orElse(DEFAULT_TEST_USER);
            this.password = Optional.ofNullable(System.getenv(ENV_TEST_DB_PASSWORD)).orElse(DEFAULT_TEST_PASSWORD);
            this.databaseName = extractDatabaseName(jdbcUrl).orElse(DEFAULT_TEST_DB);
        }

        @Override
        public void start() {
            try (Connection connection = DriverManager.getConnection(jdbcUrl, username, password)) {
                connection.createStatement().execute("SELECT 1");
                LOGGER.info(
                    "Using locally managed PostgreSQL instance: jdbcUrl={}, username={}, database={}",
                    jdbcUrl,
                    username,
                    databaseName
                );
            } catch (SQLException exception) {
                String errorDetails = buildConnectionErrorMessage(exception);
                throw new IllegalStateException(errorDetails, exception);
            }
        }
        
        private String buildConnectionErrorMessage(SQLException exception) {
            StringBuilder message = new StringBuilder();
            message.append("Failed to connect to local PostgreSQL instance.\n\n");
            appendConnectionDetails(message);
            appendReason(message, exception);
            appendContext(message);
            appendResolutionSteps(message);
            appendEnvironmentVariables(message);
            return message.toString();
        }
        
        private void appendConnectionDetails(StringBuilder message) {
            message.append("Connection details:\n");
            message.append("  JDBC URL: ").append(jdbcUrl).append("\n");
            message.append("  Username: ").append(username).append("\n");
            message.append("  Database: ").append(databaseName).append("\n\n");
        }
        
        private void appendReason(StringBuilder message, SQLException exception) {
            message.append("Reason: ").append(exception.getMessage()).append("\n\n");
        }
        
        private void appendContext(StringBuilder message) {
            message.append("Context: ").append(reason.getDescription()).append("\n\n");
        }
        
        private void appendResolutionSteps(StringBuilder message) {
            // Prioritize Docker for local development, local PostgreSQL for cloud environments
            if (reason == LocalDatabaseReason.EXPLICIT_LOCAL_MODE) {
                // User explicitly chose local mode
                message.append("To resolve this issue (local PostgreSQL mode):\n");
                appendLocalPostgresSetupSteps(message);
                message.append("\n");
            } else {
                // Docker unavailable or failed - recommend Docker for local dev
                message.append("Recommended solution for local development:\n");
                message.append("  Install and start Docker, then re-run tests to use Testcontainers automatically.\n\n");
                message.append("Alternative (for cloud environments without Docker):\n");
                appendLocalPostgresSetupSteps(message);
                message.append("\n");
            }
        }
        
        private void appendLocalPostgresSetupSteps(StringBuilder message) {
            message.append("  1. Run 'scripts/codex-setup.sh' to set up the local PostgreSQL instance\n");
            message.append("  2. Ensure 'scripts/local-postgres.sh start' completes successfully\n");
            message.append("  3. Verify the database is running: scripts/local-postgres.sh status");
        }
        
        private void appendEnvironmentVariables(StringBuilder message) {
            message.append("Environment variables for custom configuration:\n");
            message.append("  ").append(ENV_TEST_JDBC_URL).append(": Custom JDBC URL (default: ")
                .append(DEFAULT_TEST_JDBC_URL).append(", current: ")
                .append(System.getenv(ENV_TEST_JDBC_URL) != null ? "configured" : "using default")
                .append(")\n");
            message.append("  ").append(ENV_TEST_DB_USER).append(": Custom database user (default: ")
                .append(DEFAULT_TEST_USER).append(", current: ")
                .append(System.getenv(ENV_TEST_DB_USER) != null ? "configured" : "using default")
                .append(")\n");
            message.append("  ").append(ENV_TEST_DB_PASSWORD).append(": Custom database password\n");
            message.append("  ").append(ENV_DB_MODE).append(": Set to 'local' to force local database mode\n");
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
