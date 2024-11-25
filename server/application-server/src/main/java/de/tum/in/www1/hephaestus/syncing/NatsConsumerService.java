package de.tum.in.www1.hephaestus.syncing;

import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import org.kohsuke.github.GHEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamManagement;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandlerRegistry;
import de.tum.in.www1.hephaestus.workspace.RepositoryToMonitor;

@Order(value = 1)
@Service
public class NatsConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(NatsConsumerService.class);

    private static final int INITIAL_RECONNECT_DELAY_SECONDS = 2;

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${nats.timeframe}")
    private int timeframe;

    @Value("${nats.server}")
    private String natsServer;

    @Value("${nats.durable-consumer-name}")
    private String durableConsumerName;

    private Connection natsConnection;

    private Map<Long, ConsumerContext> repositoryToMonitorIdToConsumerContext = new HashMap<>();

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
    public void startConsumingRepositoryToMonitorAsync(RepositoryToMonitor repositoryToMonitor) {
        while (true) {
            if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
                logger.info("NATS connection is not connected. Attempting to connect...");
                try {
                    natsConnection = Nats.connect(buildNatsOptions());
                    logger.info("Connected to NATS server.");
                } catch (IOException | InterruptedException e) {
                    logger.error("Failed to connect to NATS server: {}", e.getMessage(), e);
                }
            }
    
            if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
                try {
                    setupConsumer(natsConnection, repositoryToMonitor);
                    logger.info("Consumer setup successful for repository: {} with monitoring ID: {}",
                        repositoryToMonitor.getNameWithOwner(), repositoryToMonitor.getId());
                    break;
                } catch (IOException | InterruptedException e) {
                    logger.error("Failed to set up consumer for repository: {} with monitoring ID: {} - {}",
                        repositoryToMonitor.getNameWithOwner(), repositoryToMonitor.getId(), e.getMessage());
                }
            }
    
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                logger.error("Thread interrupted during sleep: {}", e.getMessage());
                break;
            }
        }
    }

    private void setupConsumer(Connection connection, RepositoryToMonitor repositoryToMonitor) throws IOException, InterruptedException {
        try {
            StreamContext streamContext = connection.getStreamContext("github");
            ConsumerContext consumerContext = null;
            var subjects = getSubjects(repositoryToMonitor.getNameWithOwner());
            
            String repositoryDurableConsumerName = "";
            if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
                repositoryDurableConsumerName = durableConsumerName + "-" + repositoryToMonitor.getId();
            }

            // Check if consumer already exists
            if (!repositoryDurableConsumerName.isEmpty()) {
                try {
                    consumerContext = streamContext.getConsumerContext(repositoryDurableConsumerName);
                    
                    var config = consumerContext.getConsumerInfo().getConsumerConfiguration();
                    var filterSubjects = config.getFilterSubjects();
                    var filterMatches = filterSubjects.containsAll(Arrays.asList(subjects)) && filterSubjects.size() == subjects.length;
                    if (!filterMatches) {
                        logger.info("Consumer exists but with different subjects. Updating consumer.");
                        consumerContext = streamContext.createOrUpdateConsumer(ConsumerConfiguration.builder(config).filterSubjects(subjects).build());
                    }
                } catch (JetStreamApiException e) {
                    logger.error("Failed to get consumer context for repository: {} with monitoring ID: {} - {}",
                        repositoryToMonitor.getNameWithOwner(), repositoryToMonitor.getId(), e.getMessage());
                }
            }

            if (consumerContext == null) {
                logger.info("Setting up consumer for subjects: {}", Arrays.toString(subjects));
                ConsumerConfiguration.Builder consumerConfigBuilder = ConsumerConfiguration.builder()
                    .filterSubjects(subjects)
                    .deliverPolicy(DeliverPolicy.ByStartTime)
                    .startTime(ZonedDateTime.now().minusDays(timeframe));

                if (!repositoryDurableConsumerName.isEmpty()) {
                    consumerConfigBuilder.durable(repositoryDurableConsumerName);
                }

                ConsumerConfiguration consumerConfig = consumerConfigBuilder.build();
                consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);
            } else {
                logger.info("Consumer already exists. Skipping consumer setup.");
            }

            repositoryToMonitorIdToConsumerContext.put(repositoryToMonitor.getId(), consumerContext);

            MessageHandler handler = this::handleMessage;
            consumerContext.consume(handler);
            logger.info("Successfully started consuming messages for repository: {} with monitoring ID: {}",
                repositoryToMonitor.getNameWithOwner(), repositoryToMonitor.getId());
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
            cleanupConsumer(natsConnection, repositoryToMonitor);
            logger.info("Consumer cleanup successful for repository: {} with monitoring ID: {}",
                repositoryToMonitor.getNameWithOwner(), repositoryToMonitor.getId());
        } catch (IOException e) {
            logger.error("Failed to clean up consumer for repository: {} with monitoring ID: {} - {}",
                repositoryToMonitor.getNameWithOwner(), repositoryToMonitor.getId(), e.getMessage());
        }
    }

    private void cleanupConsumer(Connection connection, RepositoryToMonitor repositoryToMonitor) throws IOException {
        try {
            ConsumerContext consumerContext = repositoryToMonitorIdToConsumerContext.get(repositoryToMonitor.getId());
            if (consumerContext == null) {
                logger.info("No consumer context found for repository: {} with monitoring ID: {}",
                    repositoryToMonitor.getNameWithOwner(), repositoryToMonitor.getId());
                return;
            }

            JetStreamManagement jsm = natsConnection.jetStreamManagement();
            jsm.deleteConsumer("github", consumerContext.getConsumerName());
            repositoryToMonitorIdToConsumerContext.remove(repositoryToMonitor.getId());
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

    /**
     * Subjects to monitor.
     *
     * @return The subjects to monitor.
     */
    private String[] getSubjects(String nameWithOwner) {
        String[] events = handlerRegistry
            .getSupportedEvents()
            .stream()
            .map(GHEvent::name)
            .map(String::toLowerCase)
            .toArray(String[]::new);

        return Arrays.stream(events)
            .map(event -> this.getSubjectPrefix(nameWithOwner) + "." + event)
            .toArray(String[]::new);
    }

    /**
     * Get subject prefix from ownerWithName for the given repository.
     *
     * @param nameWithOwner The owner and name of the repository, i.e. "owner/name".
     * @return The subject prefix, i.e. "github.owner.name" sanitized.
     * @throws IllegalArgumentException if the repository string is improperly
     *                                  formatted.
     */
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
