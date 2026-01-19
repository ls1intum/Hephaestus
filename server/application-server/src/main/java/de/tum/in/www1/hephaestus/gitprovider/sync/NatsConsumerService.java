package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandlerRegistry;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.NatsSubscriptionProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.NatsSubscriptionProvider.NatsSubscriptionInfo;
import de.tum.in.www1.hephaestus.gitprovider.sync.exception.NatsConnectionException;
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
import org.springframework.context.annotation.Lazy;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * NATS consumer service that manages one consumer per scope.
 *
 * <h2>Architecture</h2>
 * <ul>
 *   <li>Each scope gets exactly ONE NATS consumer</li>
 *   <li>Consumer subscribes to all repositories in the scope using wildcard subjects</li>
 *   <li>Messages are processed SEQUENTIALLY within each scope (avoids race conditions)</li>
 *   <li>Scopes process in PARALLEL using virtual threads (scales to 100s of scopes)</li>
 *   <li>Installation-level events are handled by a single global consumer</li>
 * </ul>
 *
 * <h2>Thread Safety</h2>
 * This class is thread-safe. Key synchronization mechanisms:
 * <ul>
 *   <li>{@code shuttingDown} - AtomicBoolean for shutdown coordination</li>
 *   <li>{@code scopeConsumers} - ConcurrentHashMap for consumer registry</li>
 *   <li>{@code pendingScopeSetup} - ConcurrentHashMap.newKeySet() prevents duplicate setup</li>
 *   <li>{@code connectionLock} - Guards NATS connection creation/access</li>
 *   <li>{@code installationConsumer} - volatile for safe publication</li>
 * </ul>
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@code init()} - Called on ApplicationReadyEvent, establishes NATS connection</li>
 *   <li>{@code startConsumingScope()} - Creates consumer for a scope (idempotent)</li>
 *   <li>{@code stopConsumingScope()} - Removes consumer for a scope</li>
 *   <li>{@code shutdown()} - Called on @PreDestroy, graceful cleanup of all resources</li>
 * </ol>
 *
 * <h2>Configuration</h2>
 * All configuration is read from {@code nats.*} properties in application.yml:
 * <ul>
 *   <li>{@code nats.enabled} - Master switch for NATS integration</li>
 *   <li>{@code nats.server} - NATS server URL (required when enabled)</li>
 *   <li>{@code nats.timeframe} - Days of history to replay on consumer creation</li>
 *   <li>{@code nats.durable-consumer-name} - Base name for durable consumers</li>
 *   <li>{@code nats.consumer.ack-wait-minutes} - Message acknowledgment timeout (default: 5)</li>
 *   <li>{@code nats.consumer.max-ack-pending} - Max unacked messages per consumer (default: 500)</li>
 *   <li>{@code nats.consumer.reconnect-delay-seconds} - Delay between reconnect attempts (default: 2)</li>
 *   <li>{@code nats.consumer.request-timeout-seconds} - JetStream API timeout (default: 60)</li>
 * </ul>
 *
 * @see ScopeNatsConsumer
 */
@Order(1)
@Service
public class NatsConsumerService {

    private static final Logger log = LoggerFactory.getLogger(NatsConsumerService.class);

    /** Lock for NATS connection creation to prevent race conditions. */
    private final Object connectionLock = new Object();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final boolean isNatsEnabled;
    private final int timeframe;
    private final String natsServer;
    private final String durableConsumerName;
    private final int consumerAckWaitMinutes;
    private final int maxAckPending;
    private final int reconnectDelaySeconds;
    private final int requestTimeoutSeconds;

    private Connection natsConnection;

    /** One consumer per scope - keyed by scope ID */
    private final Map<Long, ScopeConsumer> scopeConsumers = new ConcurrentHashMap<>();

    /** Tracks scope IDs currently being set up to prevent duplicate consumer creation */
    private final Set<Long> pendingScopeSetup = ConcurrentHashMap.newKeySet();

    /** Global installation consumer for installation-level events */
    private volatile ScopeConsumer installationConsumer;

    /** Virtual thread executor for scope message processing */
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final GitHubMessageHandlerRegistry handlerRegistry;
    private final NatsSubscriptionProvider subscriptionProvider;

    public NatsConsumerService(
        @Lazy GitHubMessageHandlerRegistry handlerRegistry,
        NatsSubscriptionProvider subscriptionProvider,
        @Value("${nats.enabled}") boolean isNatsEnabled,
        @Value("${nats.timeframe}") int timeframe,
        @Value("${nats.server}") String natsServer,
        @Value("${nats.durable-consumer-name}") String durableConsumerName,
        @Value("${nats.consumer.ack-wait-minutes:5}") int consumerAckWaitMinutes,
        @Value("${nats.consumer.max-ack-pending:500}") int maxAckPending,
        @Value("${nats.consumer.reconnect-delay-seconds:2}") int reconnectDelaySeconds,
        @Value("${nats.consumer.request-timeout-seconds:10}") int requestTimeoutSeconds
    ) {
        this.handlerRegistry = handlerRegistry;
        this.subscriptionProvider = subscriptionProvider;
        this.isNatsEnabled = isNatsEnabled;
        this.timeframe = timeframe;
        this.natsServer = natsServer;
        this.durableConsumerName = durableConsumerName;
        this.consumerAckWaitMinutes = consumerAckWaitMinutes;
        this.maxAckPending = maxAckPending;
        this.reconnectDelaySeconds = reconnectDelaySeconds;
        this.requestTimeoutSeconds = requestTimeoutSeconds;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!isNatsEnabled) {
            log.info("Skipped NATS initialization: reason=disabled");
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
        int attempt = 0;

        while (!shuttingDown.get()) {
            try {
                natsConnection = Nats.connect(options);
                setupInstallationConsumer();
                log.info("Established NATS connection: server={}", natsServer);
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                long delayMs = ExponentialBackoff.calculateDelay(attempt);
                log.error(
                    "Failed to connect to NATS: server={}, attempt={}, nextRetryDelayMs={}",
                    natsServer,
                    attempt,
                    delayMs,
                    e
                );
                backoffBeforeRetry(attempt);
                attempt++;
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
                    log.info("NATS connection event: type={}, port={}", type, conn.getServerInfo().getPort());
                } else {
                    log.info("NATS connection event: type={}", type);
                }
            })
            .errorListener(new JetStreamErrorListener())
            .maxReconnects(-1)
            .reconnectWait(Duration.ofSeconds(reconnectDelaySeconds))
            .connectionTimeout(Duration.ofSeconds(connectionTimeoutSeconds))
            .build();
    }

    /**
     * Starts consuming events for a scope.
     * Creates a single consumer that subscribes to ALL repositories in the scope.
     * Messages are processed sequentially to avoid race conditions.
     *
     * @param scopeId The scope ID to start consuming for
     */
    public void startConsumingScope(Long scopeId) {
        if (!isNatsEnabled || shuttingDown.get() || scopeId == null) {
            return;
        }

        // Check if consumer already exists
        if (scopeConsumers.containsKey(scopeId)) {
            log.debug("Consumer already exists: scopeId={}", scopeId);
            return;
        }

        // Use atomic add to prevent concurrent setup attempts
        if (!pendingScopeSetup.add(scopeId)) {
            log.debug("Consumer setup already in progress: scopeId={}", scopeId);
            return;
        }

        virtualThreadExecutor.submit(() -> {
            try {
                ensureNatsConnectionEstablished();
                setupScopeConsumer(scopeId);
            } catch (Exception e) {
                log.error("Failed to start consumer: scopeId={}", scopeId, e);
            } finally {
                pendingScopeSetup.remove(scopeId);
            }
        });
    }

    /**
     * Updates the subjects for an existing scope consumer.
     * Call this when repositories are added/removed from a scope.
     * NOTE: Does NOT start a consumer if one doesn't exist - use startConsumingScope for that.
     *
     * @param scopeId The scope ID with updated repositories
     */
    public void updateScopeConsumer(Long scopeId) {
        if (!isNatsEnabled || shuttingDown.get() || scopeId == null) {
            return;
        }

        ScopeConsumer existing = scopeConsumers.get(scopeId);

        if (existing == null) {
            // No consumer yet - don't start one here. Consumer will be started
            // during scope activation which happens after provisioning completes.
            log.debug("Skipped consumer update: reason=consumerNotYetCreated, scopeId={}", scopeId);
            return;
        }

        virtualThreadExecutor.submit(() -> {
            try {
                String[] newSubjects = buildScopeSubjects(scopeId);
                existing.updateSubjects(newSubjects);
                log.info("Updated consumer subjects: scopeId={}, subjectCount={}", scopeId, newSubjects.length);
            } catch (Exception e) {
                log.error("Failed to update consumer: scopeId={}", scopeId, e);
            }
        });
    }

    /**
     * Stops consuming events for a scope.
     *
     * @param scopeId The scope ID to stop consuming for
     */
    public void stopConsumingScope(Long scopeId) {
        if (scopeId == null) {
            return;
        }
        ScopeConsumer consumer = scopeConsumers.remove(scopeId);

        if (consumer == null) {
            log.debug("No consumer found: scopeId={}", scopeId);
            return;
        }

        virtualThreadExecutor.submit(() -> {
            try {
                consumer.stop();
                cleanupConsumer(consumer.consumerName);
                log.info("Stopped consumer: scopeId={}", scopeId);
            } catch (Exception e) {
                log.error("Failed to stop consumer: scopeId={}", scopeId, e);
            }
        });
    }

    // =========================================================================
    // Internal implementation
    // =========================================================================

    private void setupScopeConsumer(Long scopeId) throws IOException {
        String[] subjects = buildScopeSubjects(scopeId);

        if (subjects.length == 0) {
            log.info("Skipped consumer setup: scopeId={}, reason=no subjects", scopeId);
            return;
        }

        String consumerName = durableConsumerName + "-scope-" + scopeId;

        try {
            // Use longer timeout for scopes with many repositories
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(Duration.ofSeconds(requestTimeoutSeconds))
                .build();
            StreamContext streamContext = natsConnection.getStreamContext("github", jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            ScopeConsumer scopeConsumer = new ScopeConsumer(
                scopeId,
                consumerName,
                consumerContext,
                streamContext,
                subjects
            );
            scopeConsumer.start();

            scopeConsumers.put(scopeId, scopeConsumer);
            log.info("Started consumer: scopeId={}, subjectCount={}", scopeId, subjects.length);
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to setup consumer for scope " + scopeId, e);
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

            installationConsumer = new ScopeConsumer(null, consumerName, consumerContext, streamContext, subjects);
            installationConsumer.start();
            log.info("Started installation consumer: consumerName={}, subjectCount={}", consumerName, subjects.length);
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
                    log.info(
                        "Updating durable consumer subjects: consumerName={}, oldCount={}, newCount={}",
                        consumerName,
                        existingSubjects.size(),
                        newSubjects.size()
                    );
                    return streamContext.createOrUpdateConsumer(
                        ConsumerConfiguration.builder(config).filterSubjects(subjects).build()
                    );
                }
                log.debug("Found existing consumer: consumerName={}", consumerName);
                return consumerContext;
            }
        } catch (JetStreamApiException e) {
            // Consumer doesn't exist - fall through to create it
            log.debug("Consumer not found: consumerName={}", consumerName);
        }

        // Create new consumer (ephemeral if no durable name, durable otherwise)
        log.info(
            "Creating consumer: type={}, consumerName={}, subjectCount={}, startTimeDaysAgo={}",
            isDurable ? "durable" : "ephemeral",
            isDurable ? consumerName : "ephemeral",
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

    private String[] buildScopeSubjects(Long scopeId) {
        var subscriptionInfoOpt = subscriptionProvider.getSubscriptionInfo(scopeId);
        if (subscriptionInfoOpt.isEmpty()) {
            log.warn("No subscription info found: scopeId={}", scopeId);
            return new String[0];
        }

        NatsSubscriptionInfo subscriptionInfo = subscriptionInfoOpt.get();
        Set<String> subjects = new HashSet<>();

        // Add subjects for all repositories in the scope
        for (String repoNameWithOwner : subscriptionInfo.repositoryNamesWithOwner()) {
            subjects.addAll(Arrays.asList(getRepositorySubjects(repoNameWithOwner)));
        }

        // Add organization-level subjects if scope has an organization
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
                log.warn("No handler found for event: eventType={}", eventKey);
                msg.ack();
                return;
            }

            eventHandler.onMessage(msg);
            msg.ack();
        } catch (Exception e) {
            if (!shuttingDown.get()) {
                log.error("Failed to process message: subject={}", sanitizeForLog(msg.getSubject()), e);
            }
            msg.nak();
        }
    }

    /**
     * Ensures a NATS connection is established, creating one if necessary.
     * <p>
     * Thread-safe: Uses double-checked locking with {@code connectionLock} to
     * prevent multiple threads from racing to create connections.
     *
     * @throws NatsConnectionException if connection cannot be established
     */
    private void ensureNatsConnectionEstablished() {
        // Fast path: connection already exists and is healthy
        if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
            return;
        }

        // Slow path: acquire lock and double-check
        synchronized (connectionLock) {
            if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
                return;
            }

            log.info("Reconnecting to NATS server: server={}", natsServer);
            try {
                natsConnection = Nats.connect(buildNatsOptions());
                log.info("Connected to NATS: server={}", natsServer);
            } catch (IOException e) {
                log.error("Failed to connect to NATS: server={}", natsServer, e);
                throw new NatsConnectionException("Failed to establish NATS connection", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NatsConnectionException("Connection attempt interrupted", e);
            }
        }
    }

    private void cleanupConsumer(String consumerName) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            return;
        }

        try {
            natsConnection.jetStreamManagement().deleteConsumer("github", consumerName);
        } catch (io.nats.client.JetStreamApiException e) {
            // Error code 10014 = consumer not found - this is expected during cleanup
            if (e.getApiErrorCode() == 10014) {
                log.debug("Skipped consumer cleanup: reason=consumerAlreadyDeleted, consumerName={}", consumerName);
            } else {
                log.debug("Failed to delete consumer: consumerName={}, error={}", consumerName, e.getMessage());
            }
        } catch (Exception e) {
            log.debug("Failed to delete consumer: consumerName={}, error={}", consumerName, e.getMessage());
        }
    }

    /**
     * Sleeps using exponential backoff with jitter before retrying a failed operation.
     * <p>
     * Uses the formula: {@code wait_time = min(base_delay * 2^attempt + random(0, 1000), max_delay)}
     * <p>
     * This prevents thundering herd issues when multiple instances retry simultaneously.
     *
     * @param attempt the current retry attempt number (0-based)
     */
    private void backoffBeforeRetry(int attempt) {
        try {
            ExponentialBackoff.sleep(attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    @PreDestroy
    public void shutdown() {
        if (!isNatsEnabled) {
            return;
        }

        log.info("Shutting down NATS consumers");
        shuttingDown.set(true);

        // Stop all scope consumers
        for (var entry : scopeConsumers.entrySet()) {
            try {
                entry.getValue().stop();
                log.debug("Stopped consumer: scopeId={}", entry.getKey());
            } catch (Exception e) {
                log.debug("Failed to stop scope consumer: scopeId={}", entry.getKey(), e);
            }
        }
        scopeConsumers.clear();

        // Stop installation consumer
        if (installationConsumer != null) {
            try {
                installationConsumer.stop();
            } catch (Exception e) {
                log.debug(
                    "Failed to stop installation consumer: consumerName={}",
                    installationConsumer.consumerName,
                    e
                );
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
                log.info("Closed NATS connection: server={}", natsServer);
            } catch (Exception e) {
                log.debug("Failed to close NATS connection: server={}", natsServer, e);
            }
            natsConnection = null;
        }

        log.info("Completed NATS consumer shutdown: server={}", natsServer);
    }

    private static class JetStreamErrorListener implements ErrorListener {

        private static final Logger log = LoggerFactory.getLogger(JetStreamErrorListener.class);

        @Override
        public void errorOccurred(Connection conn, String error) {
            log.error("NATS error: error={}", error);
        }

        @Override
        public void heartbeatAlarm(
            Connection conn,
            JetStreamSubscription sub,
            long lastStreamSequence,
            long lastConsumerSequence
        ) {
            String consumerName = sub != null ? sub.getConsumerName() : "unknown";
            log.warn(
                "NATS heartbeat alarm: consumerName={}, streamSeq={}, consumerSeq={}",
                consumerName,
                lastStreamSequence,
                lastConsumerSequence
            );
        }
    }

    /**
     * Internal NATS consumer for a single scope.
     * <p>
     * This is an inner class (not static) so it can access {@link #handleMessage(Message)}
     * from the enclosing service. For a reusable standalone implementation, see
     * {@link ScopeNatsConsumer} which accepts a message handler as a parameter.
     * <p>
     * <b>Thread Safety:</b> Same guarantees as {@link ScopeNatsConsumer} - uses
     * AtomicBoolean, BlockingQueue, and synchronized lifecycle methods.
     */
    private class ScopeConsumer {

        private final Long scopeId; // null for installation consumer
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

        ScopeConsumer(
            Long scopeId,
            String consumerName,
            ConsumerContext context,
            StreamContext streamContext,
            String[] subjects
        ) {
            this.scopeId = scopeId;
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
                .name("nats-processor-" + (scopeId != null ? "scope-" + scopeId : "installation"))
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
                    log.debug("Failed to close subscription: consumerName={}", consumerName, e);
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
                    log.trace("Ignored NAK error during shutdown: consumerName={}", consumerName);
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
                    log.warn("Failed to close subscription during subject update: consumerName={}", consumerName, e);
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
         * This ensures sequential processing within a scope,
         * avoiding race conditions for shared entities.
         */
        private void processMessagesSequentially() {
            log.debug("Started message processor: consumerName={}, scopeId={}", consumerName, scopeId);

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
                    log.error("Failed to process message: consumerName={}, scopeId={}", consumerName, scopeId, e);
                }
            }

            log.debug("Stopped message processor: consumerName={}, scopeId={}", consumerName, scopeId);
        }
    }
}
