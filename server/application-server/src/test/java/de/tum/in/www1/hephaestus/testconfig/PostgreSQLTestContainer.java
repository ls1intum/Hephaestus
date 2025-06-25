package de.tum.in.www1.hephaestus.testconfig;

import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Singleton PostgreSQL container for integration tests.
 * Shared across all tests for performance improvement.
 */
public final class PostgreSQLTestContainer {

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
        PostgreSQLContainer<?> newContainer = new PostgreSQLContainer<>("postgres:16")
            .withDatabaseName("hephaestus_test")
            .withUsername("test")
            .withPassword("test");

        newContainer.start();

        // Register shutdown hook to properly close the container
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
}
