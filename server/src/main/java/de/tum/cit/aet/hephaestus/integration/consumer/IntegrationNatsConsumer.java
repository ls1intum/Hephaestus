package de.tum.cit.aet.hephaestus.integration.consumer;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.spi.NatsSubscriptionProvider;
import de.tum.cit.aet.hephaestus.integration.spi.NatsSubscriptionProvider.NatsSubscriptionInfo;
import de.tum.cit.aet.hephaestus.integration.consumer.exception.NatsConnectionException;
import de.tum.cit.aet.hephaestus.integration.handler.IntegrationMessageHandler;
import io.nats.client.Connection;
import io.nats.client.ConsumerContext;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamOptions;
import io.nats.client.Message;
import io.nats.client.Nats;
import io.nats.client.Options;
import io.nats.client.StreamContext;
import io.nats.client.api.ConsumerConfiguration;
import io.nats.client.api.DeliverPolicy;
import jakarta.annotation.PreDestroy;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Unified JetStream consumer fleet for every integration kind.
 *
 * <p>Sole consumer-side entry point for the integration framework: per-scope consumers,
 * the GitHub installation-wide consumer, lifecycle, reconnect, and the per-message
 * dispatch path. Replaces the pre-unification monolithic consumer service
 * verbatim in behaviour; the collaborator decomposition is the structural improvement.
 *
 * <h2>Collaborators</h2>
 * <ul>
 *   <li>{@link IntegrationMessageDispatcher} — vendor-agnostic subject → handler routing
 *       via the unified {@code IntegrationMessageHandlerRegistry}. Sole resolver: every
 *       production handler extends
 *       {@link de.tum.cit.aet.hephaestus.integration.handler.AbstractIntegrationMessageHandler}
 *       and the per-kind {@code SubjectParser} re-encodes the subject into an
 *       {@code EventTypeKey} the registry looks up.</li>
 *   <li>{@link IntegrationPoisonHandler} — NAK-with-backoff + ACK-after-N for messages
 *       that exhaust their redelivery budget.</li>
 *   <li>{@link IntegrationConsumerStats} — read-side surface for the actuator probe.</li>
 *   <li>{@link ConsumerSubjectMath} — pure subject-arithmetic (wildcard filters,
 *       consumer-name conventions).</li>
 *   <li>{@link ScopeConsumer} — per-scope queue + virtual-thread dispatch.</li>
 * </ul>
 *
 * <h2>Routing</h2>
 * Single resolution step: {@link IntegrationMessageDispatcher#dispatch(String)} returns
 * the unified handler (or empty if the subject is one we deliberately don't process —
 * e.g. {@code check_run}). Empty resolutions ACK as no-op rather than NACKing; unknown
 * event types are not errors.
 *
 * <h2>Lifecycle</h2>
 * <ol>
 *   <li>{@link #onApplicationReady()} — establish the NATS connection with reconnect.</li>
 *   <li>{@link #onWorkspacesInitialized(WorkspacesInitializedEvent)} — start the
 *       installation-wide consumer once workspace entities exist (the GitHub
 *       installation handler depends on workspace lookup).</li>
 *   <li>{@link #startConsumingScope(Long)} / {@link #stopConsumingScope(Long)} /
 *       {@link #updateScopeConsumer(Long)} — externally-driven scope lifecycle from
 *       the workspace lifecycle services. Idempotent and concurrency-safe.</li>
 *   <li>{@link #shutdown()} — drain all scope consumers, stop the installation
 *       consumer, close the connection.</li>
 * </ol>
 *
 * <h2>Concurrency</h2>
 * Per-scope lifecycle is gated by a {@link Set} of in-flight scope IDs ({@code pendingScopeSetup})
 * so two concurrent callers can't race to create the same JetStream consumer.
 * Connection creation is double-checked-locked. The virtual-thread executor scales linearly
 * with the number of scopes.
 *
 * <h2>Role gating</h2>
 * {@link RuntimeRole#SERVER_PROPERTY} ({@code matchIfMissing=true}). The webhook-only and
 * worker-only deploys disable the server role and this bean does not start, leaving the
 * publisher half of the pipeline running unencumbered.
 */
@Order(1)
@Service
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class IntegrationNatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(IntegrationNatsConsumer.class);

    /** Stream name used by the GitHub installation-wide consumer. */
    private static final String GITHUB_STREAM = "github";

    /** NATS client connection timeout — keep short; reconnect handles long outages. */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    /** Maximum reconnect attempts before we throw {@link NatsConnectionException}. */
    private static final int MAX_RECONNECT_ATTEMPTS = 6;

    /** Base for the reconnect exponential backoff. */
    private static final long RECONNECT_BASE_MS = 1_000L;

    /** Hard cap on the reconnect exponential backoff. */
    private static final long RECONNECT_MAX_MS = 30_000L;

    private final Object connectionLock = new Object();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /** One JetStream {@link ScopeConsumer} per scope ID. */
    private final Map<Long, ScopeConsumer> scopeConsumers = new ConcurrentHashMap<>();

    /**
     * Atomic placeholder set guarding against duplicate setup. Holding the scope ID
     * here means a setup task is in-flight on a virtual thread.
     */
    private final Set<Long> pendingScopeSetup = ConcurrentHashMap.newKeySet();

    /** Installation consumer — single-instance, mutable across restarts. */
    private volatile ScopeConsumer installationConsumer;

    /** Virtual-thread executor for scope setup and installation kicks. */
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private Connection natsConnection;

    private final NatsConnectionProperties connectionProperties;
    private final NatsConsumerProperties consumerProperties;
    private final NatsSubscriptionProvider subscriptionProvider;
    private final IntegrationMessageDispatcher dispatcher;
    private final IntegrationPoisonHandler poisonHandler;
    private final IntegrationConsumerStats stats;

    public IntegrationNatsConsumer(
        NatsConnectionProperties connectionProperties,
        NatsConsumerProperties consumerProperties,
        NatsSubscriptionProvider subscriptionProvider,
        IntegrationMessageDispatcher dispatcher,
        IntegrationPoisonHandler poisonHandler,
        IntegrationConsumerStats stats
    ) {
        this.connectionProperties = connectionProperties;
        this.consumerProperties = consumerProperties;
        this.subscriptionProvider = subscriptionProvider;
        this.dispatcher = dispatcher;
        this.poisonHandler = poisonHandler;
        this.stats = stats;
    }

    // -------------------------------------------------------------------------
    // Lifecycle
    // -------------------------------------------------------------------------

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (!connectionProperties.enabled()) {
            log.info("Integration NATS consumer disabled (hephaestus.sync.nats.enabled=false)");
            stats.setNatsConnectionStatus("DISABLED");
            return;
        }
        validateConfiguration();
        try {
            connectWithRetry();
        } catch (NatsConnectionException e) {
            // The boot listener fired in a thread Spring will swallow; if we rethrow,
            // the application keeps running with no consumer and only a generic
            // ApplicationListener exception in the log. Mark the health indicator
            // explicitly so /actuator/health surfaces the failure and the operator
            // gets a real signal instead of a silent dead consumer thread.
            stats.setNatsConnectionStatus("FAILED_BOOT");
            log.error(
                "Integration NATS consumer failed to connect after {} attempts; health indicator will report DOWN",
                MAX_RECONNECT_ATTEMPTS, e
            );
            return;
        }
        log.info(
            "Integration NATS consumer ready: server={}, awaiting WorkspacesInitializedEvent for installation consumer",
            connectionProperties.server()
        );
    }

    @EventListener(WorkspacesInitializedEvent.class)
    public void onWorkspacesInitialized(WorkspacesInitializedEvent event) {
        if (!connectionProperties.enabled() || shuttingDown.get()) {
            return;
        }
        log.info("Workspaces initialized; starting installation consumer: workspaceCount={}", event.workspaceCount());
        virtualThreadExecutor.submit(() -> {
            try {
                ensureNatsConnectionEstablished();
                setupInstallationConsumer();
            } catch (Exception e) {
                log.error("Failed to start installation consumer", e);
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        if (!connectionProperties.enabled()) {
            return;
        }
        if (!shuttingDown.compareAndSet(false, true)) {
            return;
        }
        log.info("Shutting down integration NATS consumer fleet");

        for (var entry : scopeConsumers.entrySet()) {
            try {
                entry.getValue().stop();
            } catch (Exception e) {
                log.debug("Failed to stop scope consumer: scopeId={}", entry.getKey(), e);
            }
        }
        scopeConsumers.clear();
        stats.setActiveScopeConsumerCount(0);

        ScopeConsumer installation = installationConsumer;
        if (installation != null) {
            try {
                installation.stop();
            } catch (Exception e) {
                log.debug("Failed to stop installation consumer", e);
            }
            installationConsumer = null;
            stats.setInstallationConsumerActive(false);
        }

        virtualThreadExecutor.shutdown();
        try {
            if (!virtualThreadExecutor.awaitTermination(10, TimeUnit.SECONDS)) {
                virtualThreadExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            virtualThreadExecutor.shutdownNow();
            Thread.currentThread().interrupt();
        }

        if (natsConnection != null) {
            try {
                natsConnection.close();
            } catch (Exception e) {
                log.debug("Failed to close NATS connection", e);
            }
            natsConnection = null;
        }
        stats.setNatsConnectionStatus("CLOSED");
        log.info("Integration NATS consumer fleet shut down");
    }

    // -------------------------------------------------------------------------
    // Public surface (called by workspace lifecycle services)
    // -------------------------------------------------------------------------

    /**
     * Start (or no-op if already running) a JetStream consumer for the given scope.
     * Returns immediately; the actual setup happens on a virtual thread. Safe to
     * call concurrently for the same scope — duplicate setup is suppressed.
     */
    public void startConsumingScope(Long scopeId) {
        if (!connectionProperties.enabled() || shuttingDown.get() || scopeId == null) {
            return;
        }
        if (scopeConsumers.containsKey(scopeId)) {
            log.debug("Scope consumer already exists: scopeId={}", scopeId);
            return;
        }
        if (!pendingScopeSetup.add(scopeId)) {
            log.debug("Scope consumer setup already in progress: scopeId={}", scopeId);
            return;
        }
        virtualThreadExecutor.submit(() -> {
            try {
                ensureNatsConnectionEstablished();
                setupScopeConsumer(scopeId);
            } catch (Exception e) {
                log.error("Failed to start scope consumer: scopeId={}", scopeId, e);
            } finally {
                pendingScopeSetup.remove(scopeId);
            }
        });
    }

    /**
     * Update the filter subjects on an already-running scope consumer. No-op if no
     * consumer exists yet for the scope — scope startup will pick up the latest
     * subscription info when {@link #startConsumingScope(Long)} fires.
     */
    public void updateScopeConsumer(Long scopeId) {
        if (!connectionProperties.enabled() || shuttingDown.get() || scopeId == null) {
            return;
        }
        ScopeConsumer existing = scopeConsumers.get(scopeId);
        if (existing == null) {
            log.debug("Skipped consumer update: reason=notRunning, scopeId={}", scopeId);
            return;
        }
        virtualThreadExecutor.submit(() -> {
            try {
                Optional<NatsSubscriptionInfo> infoOpt = subscriptionProvider.getSubscriptionInfo(scopeId);
                if (infoOpt.isEmpty()) {
                    log.warn("No subscription info available during update: scopeId={}", scopeId);
                    return;
                }
                String[] newSubjects = buildScopeSubjects(infoOpt.get());
                existing.updateSubjects(newSubjects);
                log.info(
                    "Updated scope consumer subjects: scopeId={}, subjectCount={}",
                    scopeId,
                    newSubjects.length
                );
            } catch (Exception e) {
                log.error("Failed to update scope consumer: scopeId={}", scopeId, e);
            }
        });
    }

    /**
     * Stop and delete the JetStream consumer for the given scope. After this returns,
     * the server-side durable is gone — a subsequent {@link #startConsumingScope(Long)}
     * will create a fresh one.
     */
    public void stopConsumingScope(Long scopeId) {
        if (scopeId == null) {
            return;
        }
        ScopeConsumer consumer = scopeConsumers.remove(scopeId);
        if (consumer == null) {
            log.debug("No scope consumer to stop: scopeId={}", scopeId);
            return;
        }
        stats.setActiveScopeConsumerCount(scopeConsumers.size());
        virtualThreadExecutor.submit(() -> {
            try {
                consumer.stop();
                cleanupConsumer(consumer.streamName(), consumer.consumerName());
                log.info("Stopped scope consumer: scopeId={}", scopeId);
            } catch (Exception e) {
                log.error("Failed to stop scope consumer: scopeId={}", scopeId, e);
            }
        });
    }

    // -------------------------------------------------------------------------
    // JetStream setup
    // -------------------------------------------------------------------------

    private void setupScopeConsumer(Long scopeId) throws IOException {
        Optional<NatsSubscriptionInfo> infoOpt = subscriptionProvider.getSubscriptionInfo(scopeId);
        if (infoOpt.isEmpty()) {
            log.warn("No subscription info available: scopeId={}", scopeId);
            return;
        }
        NatsSubscriptionInfo info = infoOpt.get();
        String[] subjects = buildScopeSubjects(info);
        if (subjects.length == 0) {
            log.info("Skipped scope consumer setup (no subjects): scopeId={}", scopeId);
            return;
        }
        String streamName = info.natsStreamName();
        String consumerName = ConsumerSubjectMath.scopeConsumerName(durableBaseName(), scopeId);

        try {
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(connectionProperties.consumer().requestTimeout())
                .build();
            StreamContext streamContext = natsConnection.getStreamContext(streamName, jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            ScopeConsumer scope = new ScopeConsumer(
                scopeId,
                consumerName,
                streamName,
                consumerContext,
                streamContext,
                subjects,
                this::handleMessage
            );
            scope.start();
            scopeConsumers.put(scopeId, scope);
            stats.setActiveScopeConsumerCount(scopeConsumers.size());
            log.info(
                "Started scope consumer: scopeId={}, stream={}, subjectCount={}",
                scopeId,
                streamName,
                subjects.length
            );
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to set up scope consumer for scopeId=" + scopeId, e);
        }
    }

    private void setupInstallationConsumer() throws IOException {
        String[] subjects = new String[] { ConsumerSubjectMath.installationFilterGithub() };
        String consumerName = ConsumerSubjectMath.installationConsumerName(durableBaseName());

        try {
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(connectionProperties.consumer().requestTimeout())
                .build();
            StreamContext streamContext = natsConnection.getStreamContext(GITHUB_STREAM, jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            ScopeConsumer installation = new ScopeConsumer(
                null,
                consumerName,
                GITHUB_STREAM,
                consumerContext,
                streamContext,
                subjects,
                this::handleMessage
            );
            installation.start();
            installationConsumer = installation;
            stats.setInstallationConsumerActive(true);
            log.info("Started installation consumer: consumerName={}", consumerName);
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to set up installation consumer", e);
        }
    }

    private ConsumerContext createOrUpdateConsumer(StreamContext streamContext, String consumerName, String[] subjects)
        throws IOException, JetStreamApiException {
        boolean durable = !isBlank(connectionProperties.durableConsumerName());

        if (durable) {
            try {
                ConsumerContext existing = streamContext.getConsumerContext(consumerName);
                ConsumerConfiguration config = existing.getConsumerInfo().getConsumerConfiguration();
                Set<String> existingSubjects = new HashSet<>(config.getFilterSubjects());
                Set<String> desiredSubjects = new HashSet<>(Arrays.asList(subjects));
                if (existingSubjects.equals(desiredSubjects)) {
                    return existing;
                }
                log.info(
                    "Updating durable consumer filter subjects: consumerName={}, oldCount={}, newCount={}",
                    consumerName,
                    existingSubjects.size(),
                    desiredSubjects.size()
                );
                return streamContext.createOrUpdateConsumer(
                    ConsumerConfiguration.builder(config).filterSubjects(subjects).build()
                );
            } catch (JetStreamApiException notFound) {
                // Falls through to create-new path.
                log.debug("Durable consumer not found, creating fresh: consumerName={}", consumerName);
            }
        }

        ConsumerConfiguration.Builder builder = ConsumerConfiguration.builder()
            .filterSubjects(subjects)
            .deliverPolicy(DeliverPolicy.ByStartTime)
            .ackWait(consumerProperties.ackWait())
            .maxAckPending(consumerProperties.maxAckPending())
            .startTime(ZonedDateTime.now().minusDays(connectionProperties.replayTimeframeDays()));
        if (durable) {
            builder.durable(consumerName);
        }
        log.info(
            "Creating {} consumer: consumerName={}, subjectCount={}, replayDays={}",
            durable ? "durable" : "ephemeral",
            durable ? consumerName : "(ephemeral)",
            subjects.length,
            connectionProperties.replayTimeframeDays()
        );
        return streamContext.createOrUpdateConsumer(builder.build());
    }

    private String[] buildScopeSubjects(NatsSubscriptionInfo info) {
        String streamName = info.natsStreamName();
        Set<String> subjects = new HashSet<>();
        for (String nameWithOwner : info.repositoryNamesWithOwner()) {
            subjects.add(ConsumerSubjectMath.repositoryFilter(streamName, nameWithOwner));
        }
        if (info.hasOrganization()) {
            subjects.add(ConsumerSubjectMath.organizationFilter(streamName, info.organizationLogin()));
        }
        return subjects.toArray(String[]::new);
    }

    private String durableBaseName() {
        String name = connectionProperties.durableConsumerName();
        if (isBlank(name)) {
            return "hephaestus";
        }
        return name;
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    // -------------------------------------------------------------------------
    // Message dispatch
    // -------------------------------------------------------------------------

    /**
     * Single entry point for every queued message across every scope. The dispatcher is
     * the only resolution path; empty resolutions ACK as no-op (unknown events are not
     * errors). Exceptions land in {@link IntegrationPoisonHandler#nakWithBackoff} which
     * NAKs with exponential backoff and ACKs once the redelivery budget is exhausted.
     */
    void handleMessage(Message msg) {
        if (shuttingDown.get()) {
            safeNak(msg);
            return;
        }
        String subject = msg.getSubject();
        try {
            Optional<IntegrationMessageHandler> handler = dispatcher.dispatch(subject);
            if (handler.isEmpty()) {
                // No handler — expected for events we deliberately don't process
                // (e.g. check_run). ACK to keep the stream moving.
                log.debug("No handler for subject, ACK-as-no-op: subject={}", sanitizeForLog(subject));
                msg.ack();
                return;
            }
            handler.get().onMessage(msg);
            msg.ack();
            stats.recordDispatch(Instant.now());
        } catch (Exception e) {
            if (!shuttingDown.get()) {
                log.error("Handler failed for subject={}: {}", sanitizeForLog(subject), e.getMessage(), e);
            }
            stats.recordNak(Instant.now());
            poisonHandler.nakWithBackoff(msg);
        }
    }

    // -------------------------------------------------------------------------
    // Connection management
    // -------------------------------------------------------------------------

    private void validateConfiguration() {
        if (isBlank(connectionProperties.server())) {
            throw new IllegalStateException("hephaestus.sync.nats.server must be set when enabled=true");
        }
    }

    private void connectWithRetry() {
        Options options = buildOptions();
        int attempt = 0;
        while (!shuttingDown.get()) {
            try {
                natsConnection = Nats.connect(options);
                stats.setNatsConnectionStatus("CONNECTED");
                log.info("Established NATS connection: server={}", connectionProperties.server());
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                long delay = reconnectBackoffMs(attempt);
                stats.setNatsConnectionStatus("RECONNECTING");
                log.error(
                    "NATS connection attempt failed: server={}, attempt={}, nextDelayMs={}",
                    connectionProperties.server(),
                    attempt,
                    delay,
                    e
                );
                if (attempt >= MAX_RECONNECT_ATTEMPTS) {
                    throw new NatsConnectionException(
                        "Failed to connect to NATS after " + MAX_RECONNECT_ATTEMPTS + " attempts",
                        e
                    );
                }
                sleepUninterruptibly(delay);
                attempt++;
            }
        }
    }

    private Options buildOptions() {
        return Options.builder()
            .server(connectionProperties.server())
            .connectionListener((conn, type) -> {
                if (conn != null && conn.getStatus() != null) {
                    stats.setNatsConnectionStatus(conn.getStatus().name());
                }
                if (conn != null && conn.getServerInfo() != null) {
                    log.info("NATS connection event: type={}, port={}", type, conn.getServerInfo().getPort());
                } else {
                    log.info("NATS connection event: type={}", type);
                }
            })
            .maxReconnects(-1)
            .reconnectWait(consumerProperties.reconnectDelay())
            .connectionTimeout(CONNECTION_TIMEOUT)
            .build();
    }

    private void ensureNatsConnectionEstablished() {
        if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
            return;
        }
        synchronized (connectionLock) {
            if (natsConnection != null && natsConnection.getStatus() == Connection.Status.CONNECTED) {
                return;
            }
            try {
                natsConnection = Nats.connect(buildOptions());
                stats.setNatsConnectionStatus("CONNECTED");
            } catch (IOException e) {
                stats.setNatsConnectionStatus("DISCONNECTED");
                throw new NatsConnectionException("Failed to establish NATS connection", e);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new NatsConnectionException("Connection attempt interrupted", e);
            }
        }
    }

    private void cleanupConsumer(String streamName, String consumerName) {
        if (natsConnection == null || natsConnection.getStatus() != Connection.Status.CONNECTED) {
            return;
        }
        try {
            natsConnection.jetStreamManagement().deleteConsumer(streamName, consumerName);
        } catch (JetStreamApiException e) {
            // 10014 = consumer not found, expected on restart paths.
            if (e.getApiErrorCode() != 10014) {
                log.debug("Failed to delete consumer: consumerName={}, error={}", consumerName, e.getMessage());
            }
        } catch (Exception e) {
            log.debug("Failed to delete consumer: consumerName={}, error={}", consumerName, e.getMessage());
        }
    }

    /**
     * Full-jitter exponential backoff for connection retries. Mirrors the AWS recipe:
     * {@code clamp(base * 2^attempt + jitter, max)}.
     */
    static long reconnectBackoffMs(int attempt) {
        int clamped = Math.max(0, Math.min(attempt, 6));
        long exponential = RECONNECT_BASE_MS * (1L << clamped);
        long jitter = ThreadLocalRandom.current().nextLong(1_000L);
        return Math.min(exponential + jitter, RECONNECT_MAX_MS);
    }

    private static void sleepUninterruptibly(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void safeNak(Message msg) {
        try {
            msg.nak();
        } catch (Exception ignored) {
            // Expected during shutdown.
        }
    }

    // -------------------------------------------------------------------------
    // Test seams (package-private — used by unit tests in the same package).
    // -------------------------------------------------------------------------

    boolean isShuttingDown() {
        return shuttingDown.get();
    }

    int scopeConsumerCount() {
        return scopeConsumers.size();
    }

    boolean isInstallationActive() {
        return installationConsumer != null;
    }
}
