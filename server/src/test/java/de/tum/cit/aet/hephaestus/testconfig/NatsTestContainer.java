package de.tum.cit.aet.hephaestus.testconfig;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Singleton NATS JetStream container for integration tests, shared across all tests for speed.
 *
 * <p>Pins {@code nats:2.10-alpine} to match the deployed server version and starts it with
 * {@code -js} so JetStream (the work queue the agent pipeline relies on) is enabled. Mirrors
 * {@link PostgreSQLTestContainer}: lazy, double-checked singleton with a shutdown hook and
 * {@code withReuse} for fast re-runs.
 */
public final class NatsTestContainer {

    private static final Logger LOGGER = LoggerFactory.getLogger(NatsTestContainer.class);
    private static final DockerImageName IMAGE = DockerImageName.parse("nats:2.10-alpine");
    private static final int CLIENT_PORT = 4222;

    private static GenericContainer<?> container;

    private NatsTestContainer() {
        // Utility class — prevent instantiation.
    }

    public static GenericContainer<?> getInstance() {
        if (container == null) {
            synchronized (NatsTestContainer.class) {
                if (container == null) {
                    container = createContainer();
                }
            }
        }
        return container;
    }

    /** {@return the {@code nats://host:port} URL of the running container}. */
    public static String getServerUrl() {
        GenericContainer<?> c = getInstance();
        return "nats://" + c.getHost() + ":" + c.getMappedPort(CLIENT_PORT);
    }

    @SuppressWarnings("resource") // Lifecycle managed by the shutdown hook below.
    private static GenericContainer<?> createContainer() {
        GenericContainer<?> newContainer = new GenericContainer<>(IMAGE)
            .withCommand("-js") // enable JetStream
            .withExposedPorts(CLIENT_PORT)
            .waitingFor(Wait.forLogMessage(".*Server is ready.*", 1))
            .withReuse(true);

        newContainer.start();
        LOGGER.info(
            "Started NATS JetStream Testcontainer: server=nats://{}:{}",
            newContainer.getHost(),
            newContainer.getMappedPort(CLIENT_PORT)
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
}
