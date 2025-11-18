package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandlerRegistry;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import org.kohsuke.github.GHEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Order(value = 1)
@Service
public class NatsConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(NatsConsumerService.class);
    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;
    private static final int RECONNECT_SLEEP_MS = 2000;

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${nats.timeframe}")
    private int timeframe;

    @Value("${nats.server}")
    private String natsServer;

    @Value("${nats.durable-consumer-name}")
    private String durableConsumerName;

    private Connection natsConnection;
    private final Map<String, ConsumerContext> repositoryToMonitorIdToConsumerContext = new HashMap<>();
    private final GitHubMessageHandlerRegistry handlerRegistry;

    public NatsConsumerService(GitHubMessageHandlerRegistry handlerRegistry) {
        this.handlerRegistry = handlerRegistry;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!isNatsEnabled) {
            logger.info("NATS is disabled. Skipping initialization.");
            return;
        }

        validateConfigurations();
        Options options = buildNatsOptions();

        while (true) {
            try {
                natsConnection = Nats.connect(options);
                // Start global installation consumer once we're connected
                try {
                    setupInstallationConsumer(natsConnection);
                    logger.info("Installation consumer setup successful");
                } catch (IOException e) {
                    logger.error("Failed to set up installation consumer: {}", e.getMessage());
                }
                return;
            } catch (IOException | InterruptedException e) {
                logger.error("NATS connection error: {}", e.getMessage(), e);
            }
        }
    }

    private void validateConfigurations() {
        if (natsServer == null || natsServer.trim().isEmpty()) {
            throw new IllegalArgumentException("NATS server configuration is missing.");
        }
    }

    private Options buildNatsOptions() {
        return Options.builder()
            .server(natsServer)
            .connectionListener((conn, type) ->
                logger.info("Connection event - Server: {}, {}", conn.getServerInfo().getPort(), type)
            )
            .maxReconnects(-1)
            .reconnectWait(Duration.ofSeconds(INITIAL_RECONNECT_DELAY_SECONDS))
            .build();
    }

    @Async
    public void startConsumingOrganizationAsync(String owner) {
        startConsuming(
            () -> {
                try {
                    setupOrganizationConsumer(natsConnection, owner);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            String.format("organization: %s", owner)
        );
    }

    @Async
    public void startConsumingRepositoryToMonitorAsync(RepositoryToMonitor repositoryToMonitor) {
        startConsuming(
            () -> {
                try {
                    setupRepositoryConsumer(natsConnection, repositoryToMonitor);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            },
            String.format(
                "repository: %s with monitoring ID: %s",
                repositoryToMonitor.getNameWithOwner(),
                repositoryToMonitor.getId()
            )
        );
    }

    private void startConsuming(Runnable setupConsumer, String consumerName) {
        while (true) {
            ensureNatsConnectionEstablished();
            if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
                try {
                    setupConsumer.run();
                    logger.info("Consumer setup successful for {}", consumerName);
                    break;
                } catch (Exception e) {
                    logger.error("Failed to set up consumer for {} - {}", consumerName, e.getMessage());
                }
            }

            try {
                Thread.sleep(RECONNECT_SLEEP_MS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Thread interrupted during sleep: {}", e.getMessage());
                break;
            }
        }
    }

    private void ensureNatsConnectionEstablished() {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            logger.info("NATS connection is not connected. Attempting to connect...");
            try {
                natsConnection = Nats.connect(buildNatsOptions());
                logger.info("Connected to NATS server.");
            } catch (IOException | InterruptedException e) {
                logger.error("Failed to connect to NATS server: {}", e.getMessage(), e);
            }
        }
    }

    private void setupRepositoryConsumer(Connection connection, RepositoryToMonitor repositoryToMonitor)
        throws IOException {
        setupConsumer(
            connection,
            repositoryToMonitor.getNameWithOwner(),
            repositoryToMonitor.getId().toString(),
            getRepositorySubjects(repositoryToMonitor.getNameWithOwner())
        );
    }

    private void setupOrganizationConsumer(Connection connection, String owner) throws IOException {
        setupConsumer(connection, owner, owner, getOrganizationSubjects(owner));
    }

    private void setupInstallationConsumer(Connection connection) throws IOException {
        setupConsumer(connection, "installation", "installation", getInstallationSubjects());
    }

    private void setupConsumer(Connection connection, String name, String id, String[] subjects) throws IOException {
        try {
            StreamContext streamContext = connection.getStreamContext("github");
            ConsumerContext consumerContext = null;

            String fullDurableName = durableConsumerName != null && !durableConsumerName.isEmpty()
                ? durableConsumerName + "-" + id
                : "";

            if (!fullDurableName.isEmpty()) {
                try {
                    consumerContext = streamContext.getConsumerContext(fullDurableName);
                    var config = consumerContext.getConsumerInfo().getConsumerConfiguration();
                    var filterSubjects = config.getFilterSubjects();
                    boolean filterMatches = new HashSet<>(filterSubjects).containsAll(Arrays.asList(subjects)) &&
                    filterSubjects.size() == subjects.length;
                    if (!filterMatches) {
                        logger.info("Consumer exists but with different subjects. Updating consumer.");
                        consumerContext = streamContext.createOrUpdateConsumer(
                            ConsumerConfiguration.builder(config).filterSubjects(subjects).build()
                        );
                    }
                } catch (JetStreamApiException e) {
                    logger.error(
                        "Failed to get consumer context for name: {} with monitoring ID: {} - {}",
                        name,
                        id,
                        e.getMessage()
                    );
                }
            }

            if (consumerContext == null) {
                logger.info("Setting up consumer for subjects: {}", Arrays.toString(subjects));
                ConsumerConfiguration.Builder configBuilder = ConsumerConfiguration.builder()
                    .filterSubjects(subjects)
                    .deliverPolicy(DeliverPolicy.ByStartTime)
                    .startTime(ZonedDateTime.now().minusDays(timeframe));

                if (!fullDurableName.isEmpty()) {
                    configBuilder.durable(fullDurableName);
                }

                consumerContext = streamContext.createOrUpdateConsumer(configBuilder.build());
            } else {
                logger.info("Consumer already exists. Skipping consumer setup.");
            }

            repositoryToMonitorIdToConsumerContext.put(id, consumerContext);
            consumerContext.consume(this::handleMessage);

            logger.info("Successfully started consuming messages for name: {} with monitoring ID: {}", name, id);
        } catch (JetStreamApiException e) {
            logger.error("JetStream API exception: {}", e.getMessage(), e);
            throw new IOException("Failed to set up consumer.", e);
        }
    }

    public void stopConsumingRepositoryToMonitorAsync(RepositoryToMonitor repositoryToMonitor) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            logger.info("NATS connection is not initialized. No need to stop the consumer.");
            return;
        }

        try {
            cleanupConsumer(repositoryToMonitor);
            logger.info(
                "Consumer cleanup successful for repository: {} with monitoring ID: {}",
                repositoryToMonitor.getNameWithOwner(),
                repositoryToMonitor.getId()
            );
        } catch (IOException e) {
            logger.error(
                "Failed to clean up consumer for repository: {} with monitoring ID: {} - {}",
                repositoryToMonitor.getNameWithOwner(),
                repositoryToMonitor.getId(),
                e.getMessage()
            );
        }
    }

    private void cleanupConsumer(RepositoryToMonitor repositoryToMonitor) throws IOException {
        try {
            String id = repositoryToMonitor.getId().toString();
            ConsumerContext context = repositoryToMonitorIdToConsumerContext.get(id);
            if (context == null) {
                logger.info(
                    "No consumer context found for repository: {} with monitoring ID: {}",
                    repositoryToMonitor.getNameWithOwner(),
                    id
                );
                return;
            }

            natsConnection.jetStreamManagement().deleteConsumer("github", context.getConsumerName());
            repositoryToMonitorIdToConsumerContext.remove(id);
        } catch (JetStreamApiException e) {
            logger.error("JetStream API exception: {}", e.getMessage(), e);
            throw new IOException("Failed to clean up consumer or consumer does not exist.", e);
        }
    }

    private void handleMessage(Message msg) {
        try {
            String subject = msg.getSubject();
            String lastPart = subject.substring(subject.lastIndexOf(".") + 1);
            GHEvent eventType = GHEvent.valueOf(lastPart.toUpperCase());
            GitHubMessageHandler<?> eventHandler = handlerRegistry.getHandler(eventType);

            if (eventHandler == null) {
                logger.warn("No handler found for event type: {}", eventType);
                msg.ack();
                return;
            }

            eventHandler.onMessage(msg);
            msg.ack();
        } catch (IllegalArgumentException e) {
            logger.error("Invalid event type in subject '{}': {}", msg.getSubject(), e.getMessage());
            msg.nak();
        } catch (Exception e) {
            logger.error("Error processing message: {}", e.getMessage(), e);
            msg.nak();
        }
    }

    private String[] getOrganizationSubjects(String owner) {
        return handlerRegistry
            .getSupportedOrganizationEvents()
            .stream()
            .map(GHEvent::name)
            .map(String::toLowerCase)
            .map(event -> getSubjectPrefix(owner + "/?") + "." + event)
            .toArray(String[]::new);
    }

    private String[] getRepositorySubjects(String nameWithOwner) {
        return handlerRegistry
            .getSupportedRepositoryEvents()
            .stream()
            .map(GHEvent::name)
            .map(String::toLowerCase)
            .map(event -> getSubjectPrefix(nameWithOwner) + "." + event)
            .toArray(String[]::new);
    }

    private String[] getInstallationSubjects() {
        return handlerRegistry
            .getSupportedInstallationEvents()
            .stream()
            .map(GHEvent::name)
            .map(String::toLowerCase)
            .map(event -> getSubjectPrefix("?/?") + "." + event)
            .toArray(String[]::new);
    }

    private String getSubjectPrefix(String nameWithOwner) {
        if (nameWithOwner == null || nameWithOwner.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository identifier cannot be null or empty.");
        }

        String sanitized = nameWithOwner.replace(".", "~");
        String[] parts = sanitized.split("/");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                String.format("Invalid repository format: '%s'. Expected format 'owner/repository'.", nameWithOwner)
            );
        }

        return "github." + parts[0] + "." + parts[1];
    }
}
