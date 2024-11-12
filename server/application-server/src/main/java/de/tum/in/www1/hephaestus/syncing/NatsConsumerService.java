package de.tum.in.www1.hephaestus.syncing;

import java.io.IOException;
import java.util.Arrays;
import java.util.Set;
import java.time.Duration;
import java.time.ZonedDateTime;

import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.Message;
import io.nats.client.MessageHandler;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;

import org.springframework.stereotype.Service;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.kohsuke.github.GHEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;

import de.tum.in.www1.hephaestus.admin.AdminService;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandlerRegistry;

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

    @Autowired
    private AdminService adminService;

    private Connection natsConnection;
    private ConsumerContext consumerContext;

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
                setupConsumer(natsConnection);
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
        Set<String> repositoriesToMonitor = adminService.getAdminConfig().getRepositoriesToMonitor();
        if (repositoriesToMonitor == null || repositoriesToMonitor.size() == 0) {
            throw new IllegalArgumentException("No repositories to monitor are configured.");
        }
    }

    private Options buildNatsOptions() {
        return Options.builder()
                .server(natsServer)
                .connectionListener((conn, type) -> logger.info("Connection event - Server: {}, {}",
                        conn.getServerInfo().getPort(), type))
                .maxReconnects(-1)
                .reconnectWait(Duration.ofSeconds(INITIAL_RECONNECT_DELAY_SECONDS))
                .build();
    }

    private void setupConsumer(Connection connection) throws IOException, InterruptedException {
        try {
            StreamContext streamContext = connection.getStreamContext("github");

            // Check if consumer already exists
            if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
                try {
                    consumerContext = streamContext.getConsumerContext(durableConsumerName);
                } catch (JetStreamApiException e) {
                    consumerContext = null;
                }
            }

            if (consumerContext == null) {
                logger.info("Setting up consumer for subjects: {}", Arrays.toString(getSubjects()));
                ConsumerConfiguration.Builder consumerConfigBuilder = ConsumerConfiguration.builder()
                        .filterSubjects(getSubjects())
                        .deliverPolicy(DeliverPolicy.ByStartTime)
                        .startTime(ZonedDateTime.now().minusDays(timeframe));

                if (durableConsumerName != null && !durableConsumerName.isEmpty()) {
                    consumerConfigBuilder.durable(durableConsumerName);
                }

                ConsumerConfiguration consumerConfig = consumerConfigBuilder.build();
                consumerContext = streamContext.createOrUpdateConsumer(consumerConfig);
            } else {
                logger.info("Consumer already exists. Skipping consumer setup.");
            }

            MessageHandler handler = this::handleMessage;
            consumerContext.consume(handler);
            logger.info("Successfully started consuming messages.");
        } catch (JetStreamApiException e) {
            logger.error("JetStream API exception: {}", e.getMessage(), e);
            throw new IOException("Failed to set up consumer.", e);
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
    private String[] getSubjects() {
        String[] events = handlerRegistry.getSupportedEvents().stream()
                .map(GHEvent::name)
                .map(String::toLowerCase)
                .toArray(String[]::new);

        return adminService.getAdminConfig().getRepositoriesToMonitor().stream()
                .map(this::getSubjectPrefix)
                .flatMap(prefix -> Arrays.stream(events).map(event -> prefix + "." + event))
                .toArray(String[]::new);
    }

    /**
     * Get subject prefix from ownerWithName for the given repository.
     * 
     * @param ownerWithName The owner and name of the repository.
     * @return The subject prefix, i.e. "github.owner.name" sanitized.
     * @throws IllegalArgumentException if the repository string is improperly
     *                                  formatted.
     */
    private String getSubjectPrefix(String ownerWithName) {
        if (ownerWithName == null || ownerWithName.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository identifier cannot be null or empty.");
        }

        String sanitized = ownerWithName.replace(".", "~");
        String[] parts = sanitized.split("/");

        if (parts.length != 2) {
            throw new IllegalArgumentException(
                    String.format("Invalid repository format: '%s'. Expected format 'owner/repository'.",
                            ownerWithName));
        }

        return "github." + parts[0] + "." + parts[1];
    }
}