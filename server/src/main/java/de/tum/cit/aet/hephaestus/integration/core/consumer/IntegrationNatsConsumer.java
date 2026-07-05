package de.tum.cit.aet.hephaestus.integration.core.consumer;

import static de.tum.cit.aet.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.cit.aet.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.cit.aet.hephaestus.core.runtime.RuntimeRole;
import de.tum.cit.aet.hephaestus.integration.core.consumer.NatsConnectionException;
import de.tum.cit.aet.hephaestus.integration.core.handler.IntegrationMessageHandler;
import de.tum.cit.aet.hephaestus.integration.core.spi.IntegrationKind;
import de.tum.cit.aet.hephaestus.integration.core.spi.NatsSubscriptionProvider;
import de.tum.cit.aet.hephaestus.integration.core.spi.NatsSubscriptionProvider.NatsSubscriptionInfo;
import de.tum.cit.aet.hephaestus.integration.core.spi.NatsSubscriptionProvider.StreamSubscription;
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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Service;

/**
 * Unified JetStream consumer fleet for every integration kind. Sole consumer-side entry
 * point: per-scope consumers, the installation-wide consumer, lifecycle, reconnect, and
 * the per-message dispatch path.
 *
 * <h2>Collaborators</h2>
 * <ul>
 *   <li>{@link IntegrationMessageDispatcher} — vendor-agnostic subject → handler routing.</li>
 *   <li>{@link IntegrationPoisonHandler} — NAK-with-backoff + ACK-after-N on redelivery exhaustion.</li>
 *   <li>{@link IntegrationConsumerStats} — read-side surface for the actuator probe.</li>
 *   <li>{@link ConsumerSubjectMath} — pure subject-arithmetic (wildcard filters, consumer-name conventions).</li>
 *   <li>{@link ScopeConsumer} — per-scope queue + virtual-thread dispatch.</li>
 * </ul>
 *
 * <h2>Concurrency</h2>
 * Per-scope lifecycle is gated by an in-flight {@link Set} ({@code pendingScopeSetup}) so
 * two callers can't race to create the same JetStream consumer. Connection creation is
 * double-checked-locked. The virtual-thread executor scales linearly with the scope count.
 */
@Order(1)
@Service
@ConditionalOnProperty(name = RuntimeRole.SERVER_PROPERTY, havingValue = "true", matchIfMissing = true)
public class IntegrationNatsConsumer {

    private static final Logger log = LoggerFactory.getLogger(IntegrationNatsConsumer.class);

    /**
     * Kind whose stream backs the installation-wide consumer. Today only GitHub
     * publishes installation events; we go through {@link ConsumerSubjectMath#streamNameFor}
     * so the field name and consumer wiring stay vendor-neutral while the singular
     * supported value remains explicit. New installation-capable vendors will need a
     * sibling consumer (and likely a different field), not an in-place rename.
     */
    private static final IntegrationKind INSTALLATION_AWARE_KIND = IntegrationKind.GITHUB;

    /**
     * Kind whose stream backs the fleet-wide flat (non-repository-scoped) consumer. Today only
     * the messaging kind (Slack) publishes to a flat {@code <stream>.>} subject space; we go
     * through {@link ConsumerSubjectMath#streamNameFor} so the field name and wiring stay
     * vendor-neutral while the singular supported value stays explicit (mirrors
     * {@link #INSTALLATION_AWARE_KIND}). New flat-stream vendors will need a sibling consumer.
     */
    private static final IntegrationKind FLAT_STREAM_KIND = IntegrationKind.SLACK;

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

    /**
     * JetStream consumers per scope ID. A scope may bind to more than one stream (an SCM stream plus
     * the {@code outline} stream), so each scope maps to a list of {@link ScopeConsumer}s — one per
     * (scope, stream) with a stream-suffixed durable name so SCM and Outline durables never collide.
     */
    private final Map<Long, List<ScopeConsumer>> scopeConsumers = new ConcurrentHashMap<>();

    /**
     * Atomic placeholder set guarding against duplicate setup. Holding the scope ID
     * here means a setup task is in-flight on a virtual thread.
     */
    private final Set<Long> pendingScopeSetup = ConcurrentHashMap.newKeySet();

    /** Installation consumer — single-instance, mutable across restarts. */
    private volatile ScopeConsumer installationConsumer;

    /**
     * Fleet-wide flat-stream consumer — single-instance, subscribes to {@code <stream>.>} for
     * {@link #FLAT_STREAM_KIND}. Flat-stream subjects are not repository-scoped, so (like the
     * installation consumer) one consumer resolves the tenant inside its handler. Null unless the
     * flat-stream kind is enabled.
     */
    private volatile ScopeConsumer flatStreamConsumer;

    /** Virtual-thread executor for scope setup and installation kicks. */
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private Connection natsConnection;

    private final NatsConnectionProperties connectionProperties;
    private final NatsConsumerProperties consumerProperties;
    private final NatsSubscriptionProvider subscriptionProvider;
    private final IntegrationMessageDispatcher dispatcher;
    private final IntegrationPoisonHandler poisonHandler;
    private final IntegrationConsumerStats stats;

    /**
     * Whether the fleet-wide flat-stream consumer should be started. Gated on the vendor-neutral
     * {@code hephaestus.integration.flat-stream.enabled} (driven by the messaging integrations —
     * today only Slack) so deployments without a flat-stream kind never create a consumer on the
     * flat stream (which they also never bootstrap).
     */
    private final boolean flatStreamConsumerEnabled;

    public IntegrationNatsConsumer(
        NatsConnectionProperties connectionProperties,
        NatsConsumerProperties consumerProperties,
        NatsSubscriptionProvider subscriptionProvider,
        IntegrationMessageDispatcher dispatcher,
        IntegrationPoisonHandler poisonHandler,
        IntegrationConsumerStats stats,
        @Value("${hephaestus.integration.flat-stream.enabled:false}") boolean flatStreamConsumerEnabled
    ) {
        this.connectionProperties = connectionProperties;
        this.consumerProperties = consumerProperties;
        this.subscriptionProvider = subscriptionProvider;
        this.dispatcher = dispatcher;
        this.poisonHandler = poisonHandler;
        this.stats = stats;
        this.flatStreamConsumerEnabled = flatStreamConsumerEnabled;
    }

    // Lifecycle

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
                MAX_RECONNECT_ATTEMPTS,
                e
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
        if (flatStreamConsumerEnabled) {
            virtualThreadExecutor.submit(() -> {
                try {
                    ensureNatsConnectionEstablished();
                    setupFlatStreamConsumer();
                } catch (Exception e) {
                    log.error("Failed to start flat-stream consumer", e);
                }
            });
        }
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
            for (ScopeConsumer consumer : entry.getValue()) {
                try {
                    consumer.stop();
                } catch (Exception e) {
                    log.debug("Failed to stop scope consumer: scopeId={}", entry.getKey(), e);
                }
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

        ScopeConsumer flatStream = flatStreamConsumer;
        if (flatStream != null) {
            try {
                flatStream.stop();
            } catch (Exception e) {
                log.debug("Failed to stop flat-stream consumer", e);
            }
            flatStreamConsumer = null;
            stats.setFlatStreamConsumerActive(false);
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

    // Public surface (called by workspace lifecycle services)

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
                reconcileScope(scopeId);
            } catch (Exception e) {
                log.error("Failed to start scope consumer: scopeId={}", scopeId, e);
            } finally {
                pendingScopeSetup.remove(scopeId);
            }
        });
    }

    /**
     * Reconcile the filter subjects of an already-running scope. No-op if the scope has no consumers
     * yet — scope startup picks up the latest subscription info when {@link #startConsumingScope(Long)}
     * fires. When a scope gains a stream (e.g. Outline is connected after boot) the reconcile creates
     * the additional consumer; when it loses one the reconcile tears that consumer down.
     */
    public void updateScopeConsumer(Long scopeId) {
        if (!connectionProperties.enabled() || shuttingDown.get() || scopeId == null) {
            return;
        }
        if (!scopeConsumers.containsKey(scopeId)) {
            log.debug("Skipped consumer update: reason=notRunning, scopeId={}", scopeId);
            return;
        }
        virtualThreadExecutor.submit(() -> {
            try {
                reconcileScope(scopeId);
            } catch (Exception e) {
                log.error("Failed to update scope consumer: scopeId={}", scopeId, e);
            }
        });
    }

    /**
     * Stop and delete every JetStream consumer for the given scope. After this returns, the
     * server-side durables are gone — a subsequent {@link #startConsumingScope(Long)} recreates them.
     */
    public void stopConsumingScope(Long scopeId) {
        if (scopeId == null) {
            return;
        }
        List<ScopeConsumer> consumers = scopeConsumers.remove(scopeId);
        if (consumers == null || consumers.isEmpty()) {
            log.debug("No scope consumer to stop: scopeId={}", scopeId);
            return;
        }
        stats.setActiveScopeConsumerCount(totalConsumerCount());
        virtualThreadExecutor.submit(() -> {
            for (ScopeConsumer consumer : consumers) {
                stopAndCleanup(consumer);
            }
            log.info("Stopped scope consumers: scopeId={}, count={}", scopeId, consumers.size());
        });
    }

    // JetStream setup

    /**
     * Bring the scope's set of JetStream consumers in line with its current subscription info: one
     * consumer per (scope, stream), keyed by stream name with a stream-suffixed durable so SCM and
     * Outline durables never collide. Existing consumers whose stream is still desired are updated
     * in place; new streams create a fresh consumer; streams no longer desired are torn down.
     */
    private void reconcileScope(Long scopeId) throws IOException {
        Optional<NatsSubscriptionInfo> infoOpt = subscriptionProvider.getSubscriptionInfo(scopeId);
        if (infoOpt.isEmpty()) {
            log.warn("No subscription info available: scopeId={}", scopeId);
            return;
        }
        Map<String, StreamSubscription> desired = new LinkedHashMap<>();
        for (StreamSubscription sub : infoOpt.get().streamSubscriptions()) {
            if (!sub.subjects().isEmpty()) {
                desired.put(sub.streamName(), sub);
            }
        }

        List<ScopeConsumer> current = scopeConsumers.getOrDefault(scopeId, List.of());
        List<ScopeConsumer> next = new ArrayList<>();
        Set<String> kept = new HashSet<>();
        for (ScopeConsumer existing : current) {
            StreamSubscription want = desired.get(existing.streamName());
            if (want == null) {
                stopAndCleanup(existing);
                continue;
            }
            try {
                existing.updateSubjects(want.subjects().toArray(String[]::new));
            } catch (JetStreamApiException e) {
                throw new IOException("Failed to update scope consumer for scopeId=" + scopeId, e);
            }
            next.add(existing);
            kept.add(existing.streamName());
        }
        for (StreamSubscription want : desired.values()) {
            if (!kept.contains(want.streamName())) {
                next.add(createScopeConsumer(scopeId, want));
            }
        }

        if (next.isEmpty()) {
            scopeConsumers.remove(scopeId);
            log.info("Skipped scope consumer setup (no subjects): scopeId={}", scopeId);
        } else {
            scopeConsumers.put(scopeId, next);
        }
        stats.setActiveScopeConsumerCount(totalConsumerCount());
    }

    private ScopeConsumer createScopeConsumer(Long scopeId, StreamSubscription subscription) throws IOException {
        String streamName = subscription.streamName();
        String[] subjects = subscription.subjects().toArray(String[]::new);
        // Stream-suffixed durable so a scope's SCM and Outline consumers never share a durable name.
        String consumerName = ConsumerSubjectMath.scopeConsumerName(durableBaseName(), scopeId) + "-" + streamName;
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
            log.info(
                "Started scope consumer: scopeId={}, stream={}, subjectCount={}",
                scopeId,
                streamName,
                subjects.length
            );
            return scope;
        } catch (JetStreamApiException e) {
            throw new IOException(
                "Failed to set up scope consumer for scopeId=" + scopeId + " stream=" + streamName,
                e
            );
        }
    }

    private void stopAndCleanup(ScopeConsumer consumer) {
        try {
            consumer.stop();
            cleanupConsumer(consumer.streamName(), consumer.consumerName());
        } catch (Exception e) {
            log.error("Failed to stop scope consumer: consumerName={}", consumer.consumerName(), e);
        }
    }

    private int totalConsumerCount() {
        int total = 0;
        for (List<ScopeConsumer> list : scopeConsumers.values()) {
            total += list.size();
        }
        return total;
    }

    private void setupInstallationConsumer() throws IOException {
        String streamName = ConsumerSubjectMath.streamNameFor(INSTALLATION_AWARE_KIND).orElseThrow(() ->
            new IllegalStateException("No NATS stream resolved for installation-aware kind=" + INSTALLATION_AWARE_KIND)
        );
        String[] subjects = new String[] {
            ConsumerSubjectMath.installationAwareSubjectFilter(INSTALLATION_AWARE_KIND),
        };
        String consumerName = ConsumerSubjectMath.installationConsumerName(durableBaseName());

        try {
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(connectionProperties.consumer().requestTimeout())
                .build();
            StreamContext streamContext = natsConnection.getStreamContext(streamName, jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            ScopeConsumer installation = new ScopeConsumer(
                null,
                consumerName,
                streamName,
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

    private void setupFlatStreamConsumer() throws IOException {
        String streamName = ConsumerSubjectMath.streamNameFor(FLAT_STREAM_KIND).orElseThrow(() ->
            new IllegalStateException("No NATS stream resolved for flat-stream kind=" + FLAT_STREAM_KIND)
        );
        String[] subjects = new String[] { ConsumerSubjectMath.flatStreamSubjectFilter(FLAT_STREAM_KIND) };
        String consumerName = ConsumerSubjectMath.flatStreamConsumerName(durableBaseName(), FLAT_STREAM_KIND);

        try {
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(connectionProperties.consumer().requestTimeout())
                .build();
            StreamContext streamContext = natsConnection.getStreamContext(streamName, jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            ScopeConsumer flatStream = new ScopeConsumer(
                null,
                consumerName,
                streamName,
                consumerContext,
                streamContext,
                subjects,
                this::handleMessage
            );
            flatStream.start();
            flatStreamConsumer = flatStream;
            stats.setFlatStreamConsumerActive(true);
            log.info("Started flat-stream consumer: consumerName={}", consumerName);
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to set up flat-stream consumer", e);
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
            } catch (JetStreamApiException e) {
                // 10014 = consumer not found: expected on first start / after cleanup, fall through
                // to the create-new path. Any other API error (auth, server fault) is a real failure
                // and must not be silently reinterpreted as "create a fresh consumer".
                if (e.getApiErrorCode() != 10014) {
                    throw e;
                }
                log.debug("Durable consumer not found, creating fresh: consumerName={}", consumerName);
            }
        }

        ConsumerConfiguration configuration = newConsumerConfiguration(
            subjects,
            consumerProperties,
            durable ? consumerName : null
        );
        log.info(
            "Creating {} consumer: consumerName={}, subjectCount={}, deliverPolicy={}",
            durable ? "durable" : "ephemeral",
            durable ? consumerName : "(ephemeral)",
            subjects.length,
            configuration.getDeliverPolicy()
        );
        return streamContext.createOrUpdateConsumer(configuration);
    }

    static ConsumerConfiguration newConsumerConfiguration(
        String[] subjects,
        NatsConsumerProperties consumerProperties,
        String durableName
    ) {
        ConsumerConfiguration.Builder builder = ConsumerConfiguration.builder()
            .filterSubjects(subjects)
            .deliverPolicy(DeliverPolicy.New)
            .ackWait(consumerProperties.ackWait())
            .maxAckPending(consumerProperties.maxAckPending())
            // Server-side cap on redeliveries. Without this, MaxDeliver=∞ would let a
            // permanently-failing message NAK forever; the bound is enforced today only
            // by IntegrationPoisonHandler counting deliveredCount on our side, which
            // means a JetStream-side observability tool (`nats stream info`) cannot see
            // the policy. Mirrors the value the poison handler uses to ACK-terminate.
            .maxDeliver(consumerProperties.poison().maxRedeliver());
        if (!isBlank(durableName)) {
            builder.durable(durableName);
        }
        return builder.build();
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

    // Message dispatch

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

    // Connection management

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
                    log.debug("NATS connection event: type={}, port={}", type, conn.getServerInfo().getPort());
                } else {
                    log.debug("NATS connection event: type={}", type);
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

    // Test seams (package-private — used by unit tests in the same package).

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
