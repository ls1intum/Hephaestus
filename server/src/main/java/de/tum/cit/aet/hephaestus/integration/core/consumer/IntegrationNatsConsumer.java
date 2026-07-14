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
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
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
 * Unified JetStream consumer fleet for every integration kind. Sole consumer-side entry
 * point: per-scope consumers, the installation-wide consumer, lifecycle, reconnect, and
 * the per-message dispatch path.
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

    /** NATS client connection timeout — keep short; reconnect handles long outages. */
    private static final Duration CONNECTION_TIMEOUT = Duration.ofSeconds(10);

    /** Maximum reconnect attempts before we throw {@link NatsConnectionException}. */
    private static final int MAX_RECONNECT_ATTEMPTS = 6;

    /** Base for the reconnect exponential backoff. */
    private static final long RECONNECT_BASE_MS = 1_000L;

    /** Hard cap on the reconnect exponential backoff. */
    private static final long RECONNECT_MAX_MS = 30_000L;

    /**
     * Attempts a scope reconcile gets before we stop re-arming it. A scope binds several streams now
     * (an SCM stream plus {@code outline}), and a stream can be legitimately absent for a while — the
     * webhook pod creates the {@code outline} stream on ITS boot, which may land after ours. That failure
     * is transient, so the reconcile self-heals on a backoff instead of needing an app restart. Reuses the
     * connect-retry backoff curve: ~1s → ~30s, giving roughly a minute of grace.
     */
    private static final int MAX_SCOPE_RECONCILE_ATTEMPTS = 6;

    private final Object connectionLock = new Object();
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    /**
     * JetStream consumers per scope ID. A scope may bind to more than one stream (an SCM stream plus
     * the {@code outline} stream), so each scope maps to a list of {@link ScopeConsumer}s — one per
     * (scope, stream).
     */
    private final Map<Long, List<ScopeConsumer>> scopeConsumers = new ConcurrentHashMap<>();

    /**
     * Atomic placeholder set guarding against duplicate setup. Holding the scope ID
     * here means a setup task is in-flight on a virtual thread.
     */
    private final Set<Long> pendingScopeSetup = ConcurrentHashMap.newKeySet();

    /**
     * Consecutive failed reconcile attempts per scope, for the self-healing retry backoff. Cleared on the
     * first successful reconcile (and when the scope is stopped), so a later transient failure starts fresh.
     */
    private final Map<Long, Integer> scopeReconcileAttempts = new ConcurrentHashMap<>();

    /** Installation consumer — single-instance, mutable across restarts. */
    private volatile ScopeConsumer installationConsumer;

    /** Virtual-thread executor for scope setup and installation kicks. */
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    /** Delay timer for the scope-reconcile retry. Single daemon thread — it only ever submits to the executor. */
    private final ScheduledExecutorService retryScheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
        Thread thread = new Thread(runnable, "integration-consumer-retry");
        thread.setDaemon(true);
        return thread;
    });

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
        scopeReconcileAttempts.clear();
        stats.setActiveScopeConsumerCount(0);
        retryScheduler.shutdownNow();

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
     *
     * <p>Shares {@link #pendingScopeSetup} with {@link #startConsumingScope(Long)}: two concurrent
     * reconciles of one scope would race the read-modify-write on {@link #scopeConsumers} and the loser's
     * freshly-created consumer would be overwritten without {@code stop()} — leaking its virtual thread
     * and an orphaned server-side durable. A membership change that arrives while a reconcile is in
     * flight is not lost: the in-flight reconcile re-reads the subscription info, and the callers
     * (lifecycle listeners, repo monitors) re-fire on the next change.
     */
    public void updateScopeConsumer(Long scopeId) {
        if (!connectionProperties.enabled() || shuttingDown.get() || scopeId == null) {
            return;
        }
        if (!scopeConsumers.containsKey(scopeId)) {
            log.debug("Skipped consumer update: reason=notRunning, scopeId={}", scopeId);
            return;
        }
        if (!pendingScopeSetup.add(scopeId)) {
            log.debug("Scope reconcile already in progress: scopeId={}", scopeId);
            return;
        }
        virtualThreadExecutor.submit(() -> {
            try {
                reconcileScope(scopeId);
            } catch (Exception e) {
                log.error("Failed to update scope consumer: scopeId={}", scopeId, e);
            } finally {
                pendingScopeSetup.remove(scopeId);
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
        scopeReconcileAttempts.remove(scopeId);
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
     * Bring the scope's set of JetStream consumers in line with its current subscription info:
     * existing consumers whose stream is still desired are updated in place; new streams create
     * a fresh consumer; streams no longer desired are torn down.
     *
     * <h3>Partial failure</h3>
     * A scope binds several streams, so this loop can fail halfway — typically because one stream does not
     * exist yet (the {@code outline} stream is created by the webhook pod, which may boot after us). Every
     * consumer this pass already {@code start()}ed is therefore <b>committed to {@link #scopeConsumers}
     * before the failure propagates</b>. Dropping them on the floor would leave a live, untracked consumer:
     * never stopped on shutdown, invisible to {@link #updateScopeConsumer(Long)} (which would no-op as "not
     * running"), and duplicated on its own durable by the next {@link #startConsumingScope(Long)}.
     *
     * <p>The failure also re-arms a backed-off retry, so a transient one heals itself instead of requiring
     * an app restart.
     */
    void reconcileScope(Long scopeId) throws IOException {
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
        try {
            for (ScopeConsumer existing : current) {
                StreamSubscription want = desired.get(existing.streamName());
                if (want == null) {
                    stopAndCleanup(existing);
                    continue;
                }
                try {
                    existing.updateSubjects(want.subjects().toArray(String[]::new));
                } catch (JetStreamApiException | IOException e) {
                    // The consumer is still running with its previous subjects — keep it TRACKED (an
                    // untracked live consumer is exactly the leak this method exists to prevent) and let
                    // the retry re-apply the new subjects.
                    next.add(existing);
                    kept.add(existing.streamName());
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
        } catch (IOException | RuntimeException e) {
            // Commit whatever is already running (see javadoc), then re-arm and rethrow.
            commitScopeConsumers(scopeId, next);
            log.error(
                "Partial scope consumer reconcile: scopeId={}, running={}, desired={} — retrying",
                scopeId,
                next.size(),
                desired.size(),
                e
            );
            scheduleScopeReconcileRetry(scopeId);
            throw e;
        }

        commitScopeConsumers(scopeId, next);
        scopeReconcileAttempts.remove(scopeId);
    }

    /**
     * Publish this pass's consumer list for the scope. Every {@code start()}ed consumer MUST land here —
     * {@link #scopeConsumers} is the only handle by which a consumer can later be stopped or updated.
     */
    private void commitScopeConsumers(Long scopeId, List<ScopeConsumer> consumers) {
        if (consumers.isEmpty()) {
            scopeConsumers.remove(scopeId);
            log.info("Skipped scope consumer setup (no subjects): scopeId={}", scopeId);
        } else {
            scopeConsumers.put(scopeId, List.copyOf(consumers));
        }
        stats.setActiveScopeConsumerCount(totalConsumerCount());
    }

    /**
     * Re-arm a failed reconcile on the connect-retry backoff curve. Gives up (loudly) after
     * {@link #MAX_SCOPE_RECONCILE_ATTEMPTS} — an indefinite retry against a permanently-broken stream would
     * just be a log firehose; the successfully-created consumers from the partial pass keep working either way.
     */
    private void scheduleScopeReconcileRetry(Long scopeId) {
        if (shuttingDown.get() || !connectionProperties.enabled()) {
            return;
        }
        int attempt = scopeReconcileAttempts.merge(scopeId, 1, Integer::sum);
        if (attempt >= MAX_SCOPE_RECONCILE_ATTEMPTS) {
            log.error(
                "Giving up on scope consumer reconcile after {} attempts: scopeId={}; " +
                    "the scope is consuming only the streams that could be bound",
                attempt,
                scopeId
            );
            scopeReconcileAttempts.remove(scopeId);
            return;
        }
        long delayMs = reconnectBackoffMs(attempt - 1);
        log.warn("Retrying scope consumer reconcile: scopeId={}, attempt={}, delayMs={}", scopeId, attempt, delayMs);
        submitDelayed(() -> retryReconcileScope(scopeId), delayMs);
    }

    /**
     * Retry pass. Shares {@link #pendingScopeSetup} with the other reconcile entry points: if one is already
     * in flight for this scope we drop out — that pass re-reads the subscription info and re-arms its own
     * retry on failure, so nothing is lost. Unlike {@link #updateScopeConsumer(Long)} this does NOT require
     * the scope to already be in {@link #scopeConsumers}: a reconcile that failed on its very first stream
     * has no entry there and would otherwise never come back.
     */
    private void retryReconcileScope(Long scopeId) {
        if (shuttingDown.get() || !connectionProperties.enabled()) {
            return;
        }
        if (!pendingScopeSetup.add(scopeId)) {
            log.debug("Scope reconcile retry skipped: another reconcile in progress, scopeId={}", scopeId);
            return;
        }
        try {
            virtualThreadExecutor.submit(() -> {
                try {
                    ensureNatsConnectionEstablished();
                    reconcileScope(scopeId);
                } catch (Exception e) {
                    // reconcileScope already logged + re-armed; this is the executor's last-resort net.
                    log.debug("Scope consumer reconcile retry failed: scopeId={}", scopeId, e);
                } finally {
                    pendingScopeSetup.remove(scopeId);
                }
            });
        } catch (RejectedExecutionException e) {
            pendingScopeSetup.remove(scopeId); // shutting down
        }
    }

    private void submitDelayed(Runnable task, long delayMs) {
        try {
            retryScheduler.schedule(task, delayMs, TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException e) {
            log.debug("Scope reconcile retry not scheduled: consumer fleet is shutting down");
        }
    }

    /** Package-private + overridable so the reconcile's partial-failure path is unit-testable without a broker. */
    ScopeConsumer createScopeConsumer(Long scopeId, StreamSubscription subscription) throws IOException {
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

    /** Package-private + overridable so the reconcile/retry paths are unit-testable without a broker. */
    void ensureNatsConnectionEstablished() {
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

    /** The consumers currently TRACKED for a scope — i.e. the ones the fleet can still stop or update. */
    List<ScopeConsumer> trackedConsumers(Long scopeId) {
        return scopeConsumers.getOrDefault(scopeId, List.of());
    }

    boolean isInstallationActive() {
        return installationConsumer != null;
    }
}
