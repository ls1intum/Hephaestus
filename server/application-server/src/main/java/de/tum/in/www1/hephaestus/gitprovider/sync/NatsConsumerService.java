package de.tum.in.www1.hephaestus.gitprovider.sync;

import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandlerRegistry;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.NatsSubscriptionProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.NatsSubscriptionProvider.NatsSubscriptionInfo;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamOptions;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * NATS consumer service that manages one consumer per workspace.
 * <p>
 * Architecture:
 * - Each workspace gets exactly ONE NATS consumer
 * - Consumer subscribes to all repositories in the workspace using wildcard subjects
 * - Messages are processed SEQUENTIALLY within each workspace (avoids race conditions)
 * - Workspaces process in PARALLEL using virtual threads (scales to 100s of workspaces)
 * - Installation-level events are handled by a single global consumer
 * <p>
 * This design ensures:
 * - No concurrent insert conflicts for shared entities (Organization, Users)
 * - Rate limit friendly (sequential processing per workspace/installation)
 * - Scales horizontally with number of workspaces
 */
@Order(1)
@Service
public class NatsConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(NatsConsumerService.class);

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    @Value("${nats.enabled}")
    private boolean isNatsEnabled;

    @Value("${nats.timeframe}")
    private int timeframe;

    @Value("${nats.server}")
    private String natsServer;

    @Value("${nats.durable-consumer-name}")
    private String durableConsumerName;

    @Value("${nats.consumer.ack-wait-minutes:5}")
    private int consumerAckWaitMinutes;

    @Value("${nats.consumer.max-ack-pending:500}")
    private int maxAckPending;

    @Value("${nats.consumer.reconnect-delay-seconds:2}")
    private int reconnectDelaySeconds;

    @Value("${nats.consumer.request-timeout-seconds:10}")
    private int requestTimeoutSeconds;

    private Connection natsConnection;

    /** One consumer per workspace - keyed by workspace ID */
    private final Map<Long, WorkspaceConsumer> workspaceConsumers = new ConcurrentHashMap<>();

    /** Tracks workspace IDs currently being set up to prevent duplicate consumer creation */
    private final Set<Long> pendingWorkspaceSetup = ConcurrentHashMap.newKeySet();

    /** Global installation consumer for installation-level events */
    private volatile WorkspaceConsumer installationConsumer;

    /** Virtual thread executor for workspace message processing */
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final GitHubMessageHandlerRegistry handlerRegistry;
    private final NatsSubscriptionProvider subscriptionProvider;

    public NatsConsumerService(
        @org.springframework.context.annotation.Lazy GitHubMessageHandlerRegistry handlerRegistry,
        NatsSubscriptionProvider subscriptionProvider
    ) {
        this.handlerRegistry = handlerRegistry;
        this.subscriptionProvider = subscriptionProvider;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!isNatsEnabled) {
            logger.info("NATS is disabled. Skipping initialization.");
            return;
        }

        validateConfigurations();
        connectWithRetry();
    }

    private void validateConfigurations() {
        if (natsServer == null || natsServer.trim().isEmpty()) {
            throw new IllegalArgumentException("NATS server configuration is missing.");
        }
    }

    private void connectWithRetry() {
        Options options = buildNatsOptions();

        while (!shuttingDown.get()) {
            try {
                natsConnection = Nats.connect(options);
                setupInstallationConsumer();
                logger.info("NATS connection and installation consumer setup successful");
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                logger.error("NATS connection error: {}", e.getMessage());
                backoffBeforeRetry();
            }
        }
    }

    private Options buildNatsOptions() {
        // Connection timeout should be short (10s), while request timeout can be longer for JetStream API calls
        int connectionTimeoutSeconds = 10;
        return Options.builder()
            .server(natsServer)
            .connectionListener((conn, type) -> {
                if (conn != null && conn.getServerInfo() != null) {
                    logger.info("NATS connection event - Server: {}, {}", conn.getServerInfo().getPort(), type);
                } else {
                    logger.info("NATS connection event - {}", type);
                }
            })
            .errorListener(new JetStreamErrorListener())
            .maxReconnects(-1)
            .reconnectWait(Duration.ofSeconds(reconnectDelaySeconds))
            .connectionTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
            .build();
    }

    /**
     * Starts consuming events for a workspace.
     * Creates a single consumer that subscribes to ALL repositories in the workspace.
     * Messages are processed sequentially to avoid race conditions.
     *
     * @param workspaceId The workspace ID to start consuming for
     */
    public void startConsumingWorkspace(Long workspaceId) {
        if (!isNatsEnabled || shuttingDown.get() || workspaceId == null) {
            return;
        }

        // Check if consumer already exists
        if (workspaceConsumers.containsKey(workspaceId)) {
            logger.debug("Consumer already exists for workspace id={}", workspaceId);
            return;
        }

        // Use atomic add to prevent concurrent setup attempts
        if (!pendingWorkspaceSetup.add(workspaceId)) {
            logger.debug("Consumer setup already in progress for workspace id={}", workspaceId);
            return;
        }

        virtualThreadExecutor.submit(() -> {
            try {
                ensureNatsConnectionEstablished();
                setupWorkspaceConsumer(workspaceId);
            } catch (Exception e) {
                logger.error("Failed to start consumer for workspace id={}: {}", workspaceId, e.getMessage(), e);
            } finally {
                pendingWorkspaceSetup.remove(workspaceId);
            }
        });
    }

    /**
     * Updates the subjects for an existing workspace consumer.
     * Call this when repositories are added/removed from a workspace.
     * NOTE: Does NOT start a consumer if one doesn't exist - use startConsumingWorkspace for that.
     *
     * @param workspaceId The workspace ID with updated repositories
     */
    public void updateWorkspaceConsumer(Long workspaceId) {
        if (!isNatsEnabled || shuttingDown.get() || workspaceId == null) {
            return;
        }

        WorkspaceConsumer existing = workspaceConsumers.get(workspaceId);

        if (existing == null) {
            // No consumer yet - don't start one here. Consumer will be started
            // during workspace activation which happens after provisioning completes.
            logger.debug(
                "No existing consumer for workspace id={}, skipping update (consumer will be started during activation)",
                workspaceId
            );
            return;
        }

        virtualThreadExecutor.submit(() -> {
            try {
                String[] newSubjects = buildWorkspaceSubjects(workspaceId);
                existing.updateSubjects(newSubjects);
                logger.info("Updated consumer subjects for workspace id={}", workspaceId);
            } catch (Exception e) {
                logger.error("Failed to update consumer for workspace id={}: {}", workspaceId, e.getMessage(), e);
            }
        });
    }

    /**
     * Stops consuming events for a workspace.
     *
     * @param workspaceId The workspace ID to stop consuming for
     */
    public void stopConsumingWorkspace(Long workspaceId) {
        if (workspaceId == null) {
            return;
        }
        WorkspaceConsumer consumer = workspaceConsumers.remove(workspaceId);

        if (consumer == null) {
            logger.debug("No consumer found for workspace id={}", workspaceId);
            return;
        }

        virtualThreadExecutor.submit(() -> {
            try {
                consumer.stop();
                cleanupConsumer(consumer.consumerName);
                logger.info("Stopped consumer for workspace id={}", workspaceId);
            } catch (Exception e) {
                logger.error("Error stopping consumer for workspace id={}: {}", workspaceId, e.getMessage());
            }
        });
    }

    // =========================================================================
    // Internal implementation
    // =========================================================================

    private void setupWorkspaceConsumer(Long workspaceId) throws IOException {
        String[] subjects = buildWorkspaceSubjects(workspaceId);

        if (subjects.length == 0) {
            logger.info("No subjects to consume for workspace id={} - skipping consumer setup", workspaceId);
            return;
        }

        String consumerName = durableConsumerName + "-workspace-" + workspaceId;

        try {
            // Use longer timeout for workspaces with many repositories
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .build();
            StreamContext streamContext = natsConnection.getStreamContext("github", jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            WorkspaceConsumer workspaceConsumer = new WorkspaceConsumer(
                workspaceId,
                consumerName,
                consumerContext,
                streamContext,
                subjects
            );
            workspaceConsumer.start();

            workspaceConsumers.put(workspaceId, workspaceConsumer);
            logger.info("Started consumer for workspace id={} with {} subjects", workspaceId, subjects.length);
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to setup consumer for workspace " + workspaceId, e);
        }
    }

    private void setupInstallationConsumer() throws IOException {
        String[] subjects = getInstallationSubjects();
        String consumerName = durableConsumerName + "-installation";

        try {
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .build();
            StreamContext streamContext = natsConnection.getStreamContext("github", jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            installationConsumer = new WorkspaceConsumer(null, consumerName, consumerContext, streamContext, subjects);
            installationConsumer.start();
            logger.info("Started installation consumer with {} subjects", subjects.length);
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to setup installation consumer", e);
        }
    }

    private ConsumerContext createOrUpdateConsumer(StreamContext streamContext, String consumerName, String[] subjects)
        throws IOException, JetStreamApiException {
        boolean isDurable = durableConsumerName != null && !durableConsumerName.isBlank();

        try {
            if (isDurable) {
                // Try to get existing durable consumer
                ConsumerContext consumerContext = streamContext.getConsumerContext(consumerName);
                var config = consumerContext.getConsumerInfo().getConsumerConfiguration();
                var existingSubjects = new HashSet<>(config.getFilterSubjects());
                var newSubjects = new HashSet<>(Arrays.asList(subjects));

                if (!existingSubjects.equals(newSubjects)) {
                    logger.info(
                        "Updating durable consumer {} subjects: {} -> {}",
                        consumerName,
                        existingSubjects.size(),
                        newSubjects.size()
                    );
                    return streamContext.createOrUpdateConsumer(
                        ConsumerConfiguration.builder(config).filterSubjects(subjects).build()
                    );
                }
                logger.debug("Durable consumer {} already exists with correct subjects", consumerName);
                return consumerContext;
            }
        } catch (JetStreamApiException e) {
            // Consumer doesn't exist - fall through to create it
            logger.debug("Consumer {} not found, will create new one", consumerName);
        }

        // Create new consumer (ephemeral if no durable name, durable otherwise)
        logger.info(
            "Creating {} consumer{} with {} subjects, startTime=now-{}days",
            isDurable ? "durable" : "ephemeral",
            isDurable ? " " + consumerName : "",
            subjects.length,
            timeframe
        );

        ConsumerConfiguration.Builder configBuilder = ConsumerConfiguration.builder()
            .filterSubjects(subjects)
            .deliverPolicy(DeliverPolicy.ByStartTime)
            .ackWait(Duration.ofMinutes(consumerAckWaitMinutes))
            .maxAckPending(maxAckPending)
            .startTime(ZonedDateTime.now().minusDays(timeframe));

        if (isDurable) {
            configBuilder.durable(consumerName);
        }

        return streamContext.createOrUpdateConsumer(configBuilder.build());
    }

    private String[] buildWorkspaceSubjects(Long workspaceId) {
        var subscriptionInfoOpt = subscriptionProvider.getSubscriptionInfo(workspaceId);
        if (subscriptionInfoOpt.isEmpty()) {
            logger.warn("No subscription info found for workspace id={}", workspaceId);
            return new String[0];
        }

        NatsSubscriptionInfo subscriptionInfo = subscriptionInfoOpt.get();
        Set<String> subjects = new HashSet<>();

        // Add subjects for all repositories in the workspace
        for (String repoNameWithOwner : subscriptionInfo.repositoryNamesWithOwner()) {
            subjects.addAll(Arrays.asList(getRepositorySubjects(repoNameWithOwner)));
        }

        // Add organization-level subjects if workspace has an organization
        if (subscriptionInfo.hasOrganization()) {
            subjects.addAll(Arrays.asList(getOrganizationSubjects(subscriptionInfo.organizationLogin())));
        }

        return subjects.toArray(String[]::new);
    }

    private String[] getRepositorySubjects(String nameWithOwner) {
        return handlerRegistry
            .getSupportedRepositoryEvents()
            .stream()
            .map(event -> buildSubject(getSubjectPrefix(nameWithOwner), event))
            .toArray(String[]::new);
    }

    private String[] getOrganizationSubjects(String owner) {
        return handlerRegistry
            .getSupportedOrganizationEvents()
            .stream()
            .map(event -> buildSubject(getSubjectPrefix(owner + "/?"), event))
            .toArray(String[]::new);
    }

    private String[] getInstallationSubjects() {
        return handlerRegistry
            .getSupportedInstallationEvents()
            .stream()
            .map(event -> buildSubject(getSubjectPrefix("?/?"), event))
            .toArray(String[]::new);
    }

    private String buildSubject(String prefix, String eventKey) {
        return prefix + "." + eventKey.toLowerCase(Locale.ENGLISH);
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

    private void handleMessage(Message msg) {
        if (shuttingDown.get()) {
            msg.nak();
            return;
        }

        try {
            String subject = msg.getSubject();
            String eventKey = subject.substring(subject.lastIndexOf('.') + 1);
            GitHubMessageHandler<?> eventHandler = handlerRegistry.getHandler(eventKey);

            if (eventHandler == null) {
                logger.warn("No handler found for event type: {}", eventKey);
                msg.ack();
                return;
            }

            eventHandler.onMessage(msg);
            msg.ack();
        } catch (Exception e) {
            if (!shuttingDown.get()) {
                logger.error("Error processing message: {}", e.getMessage(), e);
            }
            msg.nak();
        }
    }

    private void ensureNatsConnectionEstablished() {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            logger.info("NATS connection is not connected. Attempting to connect...");
            try {
                natsConnection = Nats.connect(buildNatsOptions());
                logger.info("Connected to NATS server.");
            } catch (IOException | InterruptedException e) {
                logger.error("Failed to connect to NATS server: {}", e.getMessage());
                throw new RuntimeException("Failed to establish NATS connection", e);
            }
        }
    }

    private void cleanupConsumer(String consumerName) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            return;
        }

        try {
            natsConnection.jetStreamManagement().deleteConsumer("github", consumerName);
        } catch (Exception e) {
            logger.debug("Failed to delete consumer {}: {}", consumerName, e.getMessage());
        }
    }

    private void backoffBeforeRetry() {
        try {
            Thread.sleep(reconnectDelaySeconds * 1000L);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (!isNatsEnabled) {
            return;
        }

        logger.info("Initiating NATS consumer graceful shutdown...");
        shuttingDown.set(true);

        // Stop all workspace consumers
        for (var entry : workspaceConsumers.entrySet()) {
            try {
                entry.getValue().stop();
                logger.debug("Stopped consumer for workspace id={}", entry.getKey());
            } catch (Exception e) {
                logger.debug("Error stopping workspace consumer: {}", e.getMessage());
            }
        }
        workspaceConsumers.clear();

        // Stop installation consumer
        if (installationConsumer != null) {
            try {
                installationConsumer.stop();
            } catch (Exception e) {
                logger.debug("Error stopping installation consumer: {}", e.getMessage());
            }
            installationConsumer = null;
        }

        // Shutdown virtual thread executor
        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        // Close NATS connection
        if (natsConnection != null) {
            try {
                natsConnection.close();
                logger.info("NATS connection closed.");
            } catch (Exception e) {
                logger.debug("Error closing NATS connection: {}", e.getMessage());
            }
            natsConnection = null;
        }

        logger.info("NATS consumer shutdown complete.");
    }

    private static class JetStreamErrorListener implements ErrorListener {

        private static final Logger logger = LoggerFactory.getLogger(JetStreamErrorListener.class);

        @Override
        public void errorOccurred(Connection conn, String error) {
            logger.error("NATS error: {}", error);
        }

        @Override
        public void heartbeatAlarm(
            Connection conn,
            JetStreamSubscription sub,
            long lastStreamSequence,
            long lastConsumerSequence
        ) {
            String consumerName = sub != null ? sub.getConsumerName() : "unknown";
            logger.warn(
                "NATS heartbeat alarm for consumer {} (streamSeq={}, consumerSeq={})",
                consumerName,
                lastStreamSequence,
                lastConsumerSequence
            );
        }
    }

    /**
     * Represents a NATS consumer for a single workspace.
     * Processes messages SEQUENTIALLY to avoid race conditions.
     */
    private class WorkspaceConsumer {

        private final Long workspaceId; // null for installation consumer
        private final String consumerName;
        private final ConsumerContext context;
        private final StreamContext streamContext;
        private volatile String[] currentSubjects;
        private volatile MessageConsumer subscription;
        private final AtomicBoolean running = new AtomicBoolean(false);

        /**
         * Queue for sequential message processing.
         * Messages are added by the NATS callback and processed one at a time.
         */
        private final BlockingQueue<Message> messageQueue = new LinkedBlockingQueue<>();

        /** Virtual thread for processing messages from the queue */
        private volatile Thread processorThread;

        WorkspaceConsumer(
            Long workspaceId,
            String consumerName,
            ConsumerContext context,
            StreamContext streamContext,
            String[] subjects
        ) {
            this.workspaceId = workspaceId;
            this.consumerName = consumerName;
            this.context = context;
            this.streamContext = streamContext;
            this.currentSubjects = subjects;
        }

        synchronized void start() throws IOException, JetStreamApiException {
            if (running.get()) {
                return;
            }

            running.set(true);

            // Start the sequential message processor on a virtual thread
            processorThread = Thread.ofVirtual()
                .name("nats-processor-" + (workspaceId != null ? "ws-" + workspaceId : "installation"))
                .start(this::processMessagesSequentially);

            // Subscribe to NATS - messages go to queue for sequential processing
            subscription = context.consume(this::enqueueMessage);
        }

        synchronized void stop() {
            running.set(false);

            if (subscription != null) {
                try {
                    subscription.close();
                } catch (Exception e) {
                    logger.debug("Error closing subscription: {}", e.getMessage());
                }
                subscription = null;
            }

            // Interrupt the processor thread to wake it from blocking
            if (processorThread != null) {
                processorThread.interrupt();
                processorThread = null;
            }

            // Drain remaining messages (NAK them) - exceptions during shutdown are expected
            Message msg;
            while ((msg = messageQueue.poll()) != null) {
                try {
                    msg.nak();
                } catch (Exception e) {
                    // Expected during shutdown when connection is already closed
                    logger.trace("Ignored NAK error during shutdown: {}", e.getMessage());
                }
            }
        }

        synchronized void updateSubjects(String[] newSubjects) throws IOException, JetStreamApiException {
            if (Arrays.equals(currentSubjects, newSubjects)) {
                return;
            }

            // Update the consumer configuration with new subjects
            var config = context.getConsumerInfo().getConsumerConfiguration();
            streamContext.createOrUpdateConsumer(
                ConsumerConfiguration.builder(config).filterSubjects(newSubjects).build()
            );

            currentSubjects = newSubjects;

            // Restart subscription to pick up new subjects
            if (subscription != null) {
                try {
                    subscription.close();
                } catch (Exception e) {
                    logger.warn("Error closing subscription during subject update: {}", e.getMessage());
                }
            }
            subscription = context.consume(this::enqueueMessage);
        }

        private void enqueueMessage(Message msg) {
            if (!running.get()) {
                msg.nak();
                return;
            }

            try {
                messageQueue.put(msg);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                msg.nak();
            }
        }

        /**
         * Processes messages from the queue ONE AT A TIME.
         * This ensures sequential processing within a workspace,
         * avoiding race conditions for shared entities.
         */
        private void processMessagesSequentially() {
            String label = workspaceId != null ? "workspace-" + workspaceId : "installation";
            logger.debug("Started sequential message processor for {}", label);

            while (running.get() && !Thread.currentThread().isInterrupted()) {
                try {
                    // Block waiting for next message (with timeout for shutdown check)
                    Message msg = messageQueue.poll(1, TimeUnit.SECONDS);
                    if (msg != null) {
                        handleMessage(msg);
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    logger.error("Error in message processor for {}: {}", label, e.getMessage(), e);
                }
            }

            logger.debug("Stopped sequential message processor for {}", label);
        }
    }
}
