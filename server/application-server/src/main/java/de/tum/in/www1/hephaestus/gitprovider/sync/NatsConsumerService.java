package de.tum.in.www1.hephaestus.gitprovider.sync;

import static de.tum.in.www1.hephaestus.core.LoggingUtils.sanitizeForLog;

import de.tum.in.www1.hephaestus.core.event.WorkspacesInitializedEvent;
import de.tum.in.www1.hephaestus.gitprovider.common.github.ExponentialBackoff;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.github.GitHubMessageHandlerRegistry;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandler;
import de.tum.in.www1.hephaestus.gitprovider.common.gitlab.GitLabMessageHandlerRegistry;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.NatsSubscriptionProvider;
import de.tum.in.www1.hephaestus.gitprovider.common.spi.NatsSubscriptionProvider.NatsSubscriptionInfo;
import de.tum.in.www1.hephaestus.gitprovider.sync.exception.NatsConnectionException;
import io.nats.client.Connection;
import io.nats.client.ConsumeOptions;
import io.nats.client.ConsumerContext;
import io.nats.client.ErrorListener;
import io.nats.client.JetStreamApiException;
import io.nats.client.JetStreamOptions;
import io.nats.client.JetStreamSubscription;
import io.nats.client.Message;
import io.nats.client.MessageConsumer;
import io.nats.client.MessageHandler;
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
 *   <li>{@code onWorkspacesInitialized()} - Called after workspace provisioning, starts installation consumer</li>
 *   <li>{@code startConsumingScope()} - Creates consumer for a scope (idempotent)</li>
 *   <li>{@code stopConsumingScope()} - Removes consumer for a scope</li>
 *   <li>{@code shutdown()} - Called on @PreDestroy, graceful cleanup of all resources</li>
 * </ol>
 *
 * <h2>Startup Sequencing</h2>
 * The installation consumer only needs workspace entities to exist - it does NOT need
 * the full GraphQL sync to complete. Installation events provision new workspaces or
 * add/remove repositories, which are independent of the repository content sync.
 * <ol>
 *   <li>Application starts, NATS connection established</li>
 *   <li>WorkspaceStartupListener provisions workspaces (creates/loads from database)</li>
 *   <li>WorkspaceStartupListener publishes {@link WorkspacesInitializedEvent}</li>
 *   <li>This service receives the event and starts the installation consumer</li>
 *   <li>WorkspaceActivationService runs full GraphQL sync (in parallel with installation consumer)</li>
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

    /**
     * Maximum number of redelivery attempts before giving up on a message.
     * After this many attempts, the message is acknowledged and logged as a poison message.
     * This prevents infinite redelivery loops for messages that consistently fail.
     */
    private static final int MAX_REDELIVERY_ATTEMPTS = 10;

    /**
     * Base delay for NAK backoff in milliseconds.
     * First retry waits ~2s, second ~4s, etc. up to MAX_NAK_DELAY_MS.
     */
    private static final long NAK_BASE_DELAY_MS = 2000;

    /**
     * Maximum NAK delay cap in milliseconds (5 minutes).
     * Prevents excessively long delays that could cause message timeout.
     */
    private static final long MAX_NAK_DELAY_MS = 300_000;

    /** Lock for NATS connection creation to prevent race conditions. */
    private final Object connectionLock = new Object();

    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private final NatsProperties natsProperties;

    private Connection natsConnection;

    /** Tracks consecutive heartbeat alarms per consumer for restart decisions */
    private final Map<String, HeartbeatAlarmState> heartbeatAlarmStates = new ConcurrentHashMap<>();

    /** One consumer per scope - keyed by scope ID */
    private final Map<Long, ScopeConsumer> scopeConsumers = new ConcurrentHashMap<>();

    /** Tracks scope IDs currently being set up to prevent duplicate consumer creation */
    private final Set<Long> pendingScopeSetup = ConcurrentHashMap.newKeySet();

    /** Global installation consumer for installation-level events */
    private volatile ScopeConsumer installationConsumer;

    /** Virtual thread executor for scope message processing */
    private final ExecutorService virtualThreadExecutor = Executors.newVirtualThreadPerTaskExecutor();

    private final GitHubMessageHandlerRegistry githubHandlerRegistry;
    private final GitLabMessageHandlerRegistry gitlabHandlerRegistry;
    private final NatsSubscriptionProvider subscriptionProvider;

    public NatsConsumerService(
        @Lazy GitHubMessageHandlerRegistry githubHandlerRegistry,
        @Lazy GitLabMessageHandlerRegistry gitlabHandlerRegistry,
        NatsSubscriptionProvider subscriptionProvider,
        NatsProperties natsProperties
    ) {
        this.githubHandlerRegistry = githubHandlerRegistry;
        this.gitlabHandlerRegistry = gitlabHandlerRegistry;
        this.subscriptionProvider = subscriptionProvider;
        this.natsProperties = natsProperties;
    }

    /**
     * Initializes NATS connection on application startup.
     * <p>
     * The installation consumer is NOT started here - it waits for the
     * {@link WorkspacesInitializedEvent} which fires after workspace provisioning
     * but before the full GraphQL sync. This allows installation events to be
     * processed immediately after workspaces are loaded.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void init() {
        if (!natsProperties.enabled()) {
            log.info("Skipped NATS initialization: reason=disabled");
            return;
        }

        validateConfigurations();
        connectWithRetry();
        log.info("NATS connection established, waiting for workspaces to be initialized");
    }

    /**
     * Starts the installation consumer after workspaces are initialized.
     * <p>
     * Installation events only need workspace entities to exist - they do NOT need
     * the full repository sync to complete. This allows installation events to be
     * processed immediately after startup.
     *
     * @param event the workspaces initialized event
     */
    @EventListener(WorkspacesInitializedEvent.class)
    public void onWorkspacesInitialized(WorkspacesInitializedEvent event) {
        if (!natsProperties.enabled()) {
            return;
        }

        log.info("Workspaces initialized, starting installation consumer: workspaceCount={}", event.workspaceCount());
        startInstallationConsumer();
    }

    /**
     * Starts the installation consumer for processing installation-level webhook events.
     */
    private void startInstallationConsumer() {
        if (shuttingDown.get()) {
            return;
        }

        virtualThreadExecutor.submit(() -> {
            try {
                ensureNatsConnectionEstablished();
                setupInstallationConsumer();
            } catch (Exception e) {
                log.error("Failed to start installation consumer", e);
            }
        });
    }

    private void validateConfigurations() {
        if (natsProperties.server() == null || natsProperties.server().trim().isEmpty()) {
            throw new IllegalArgumentException("NATS server configuration is missing.");
        }
    }

    private void connectWithRetry() {
        Options options = buildNatsOptions();
        int attempt = 0;

        while (!shuttingDown.get()) {
            try {
                natsConnection = Nats.connect(options);
                log.info("Established NATS connection: server={}", natsProperties.server());
                return;
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return;
            } catch (IOException e) {
                long delayMs = ExponentialBackoff.calculateDelay(attempt);
                log.error(
                    "Failed to connect to NATS: server={}, attempt={}, nextRetryDelayMs={}",
                    natsProperties.server(),
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
            .server(natsProperties.server())
            .connectionListener((conn, type) -> {
                if (conn != null && conn.getServerInfo() != null) {
                    log.info("NATS connection event: type={}, port={}", type, conn.getServerInfo().getPort());
                } else {
                    log.info("NATS connection event: type={}", type);
                }
            })
            .errorListener(new JetStreamErrorListener())
            .maxReconnects(-1)
            .reconnectWait(natsProperties.consumer().reconnectDelay())
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
        if (!natsProperties.enabled() || shuttingDown.get() || scopeId == null) {
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
        if (!natsProperties.enabled() || shuttingDown.get() || scopeId == null) {
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
                var subscriptionInfoOpt = subscriptionProvider.getSubscriptionInfo(scopeId);
                if (subscriptionInfoOpt.isEmpty()) {
                    log.warn("No subscription info found during update: scopeId={}", scopeId);
                    return;
                }
                String[] newSubjects = buildScopeSubjects(subscriptionInfoOpt.get());
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
                cleanupConsumer(consumer.streamName, consumer.consumerName);
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
        var subscriptionInfoOpt = subscriptionProvider.getSubscriptionInfo(scopeId);
        if (subscriptionInfoOpt.isEmpty()) {
            log.warn("No subscription info found: scopeId={}", scopeId);
            return;
        }

        NatsSubscriptionInfo subscriptionInfo = subscriptionInfoOpt.get();
        String[] subjects = buildScopeSubjects(subscriptionInfo);
        String streamName = subscriptionInfo.natsStreamName();

        if (subjects.length == 0) {
            log.info("Skipped consumer setup: scopeId={}, reason=no subjects", scopeId);
            return;
        }

        String consumerName = natsProperties.durableConsumerName() + "-scope-" + scopeId;

        try {
            // Use longer timeout for scopes with many repositories
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(natsProperties.consumer().requestTimeout())
                .build();
            StreamContext streamContext = natsConnection.getStreamContext(streamName, jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            ScopeConsumer scopeConsumer = new ScopeConsumer(
                scopeId,
                consumerName,
                consumerContext,
                streamContext,
                subjects,
                streamName
            );
            scopeConsumer.start();

            scopeConsumers.put(scopeId, scopeConsumer);
            log.info("Started consumer: scopeId={}, stream={}, subjectCount={}", scopeId, streamName, subjects.length);
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to setup consumer for scope " + scopeId, e);
        }
    }

    private void setupInstallationConsumer() throws IOException {
        String[] subjects = getInstallationSubjects();
        String consumerName = natsProperties.durableConsumerName() + "-installation";

        try {
            JetStreamOptions jsOptions = JetStreamOptions.builder()
                .requestTimeout(natsProperties.consumer().requestTimeout())
                .build();
            StreamContext streamContext = natsConnection.getStreamContext("github", jsOptions);
            ConsumerContext consumerContext = createOrUpdateConsumer(streamContext, consumerName, subjects);

            installationConsumer = new ScopeConsumer(
                null,
                consumerName,
                consumerContext,
                streamContext,
                subjects,
                "github"
            );
            installationConsumer.start();
            log.info("Started installation consumer: consumerName={}, subjectCount={}", consumerName, subjects.length);
        } catch (JetStreamApiException e) {
            throw new IOException("Failed to setup installation consumer", e);
        }
    }

    private ConsumerContext createOrUpdateConsumer(StreamContext streamContext, String consumerName, String[] subjects)
        throws IOException, JetStreamApiException {
        boolean isDurable =
            natsProperties.durableConsumerName() != null && !natsProperties.durableConsumerName().isBlank();

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
            natsProperties.replayTimeframeDays()
        );

        ConsumerConfiguration.Builder configBuilder = ConsumerConfiguration.builder()
            .filterSubjects(subjects)
            .deliverPolicy(DeliverPolicy.ByStartTime)
            .ackWait(natsProperties.consumer().ackWait())
            .maxAckPending(natsProperties.consumer().maxAckPending())
            .startTime(ZonedDateTime.now().minusDays(natsProperties.replayTimeframeDays()));

        if (isDurable) {
            configBuilder.durable(consumerName);
        }

        return streamContext.createOrUpdateConsumer(configBuilder.build());
    }

    private String[] buildScopeSubjects(NatsSubscriptionInfo subscriptionInfo) {
        String streamName = subscriptionInfo.natsStreamName();
        Set<String> subjects = new HashSet<>();

        // Add subjects for all repositories in the scope
        for (String repoNameWithOwner : subscriptionInfo.repositoryNamesWithOwner()) {
            subjects.addAll(Arrays.asList(getRepositorySubjects(streamName, repoNameWithOwner)));
        }

        // Add organization-level subjects if scope has an organization
        // Note: GitLab scopes may not use org-level subjects the same way as GitHub,
        // but the wildcard approach is safe — it just won't match any messages.
        if (subscriptionInfo.hasOrganization()) {
            subjects.addAll(Arrays.asList(getOrganizationSubjects(streamName, subscriptionInfo.organizationLogin())));
        }

        return subjects.toArray(String[]::new);
    }

    /**
     * Returns a SINGLE wildcard subject that matches ALL events for a repository.
     *
     * <p>Uses NATS wildcard token {@code >} which matches one or more tokens.
     * Example: {@code github.ls1intum.Artemis.>} matches:
     * <ul>
     *   <li>{@code github.ls1intum.Artemis.issues}</li>
     *   <li>{@code github.ls1intum.Artemis.pull_request}</li>
     *   <li>{@code github.ls1intum.Artemis.push}</li>
     * </ul>
     *
     * <p><strong>Why wildcards?</strong> Without wildcards, a scope with 200 repos
     * and 12 event types creates 2,400 filter subjects. NATS JetStream consumer
     * creation validates each subject against the stream, causing O(n*m) timeouts
     * on large filter lists. Using wildcards reduces this to O(n) where n=repos.
     *
     * @param streamName    the NATS stream name ("github" or "gitlab")
     * @param nameWithOwner repository identifier in "owner/repo" format
     * @return single-element array containing the wildcard subject
     */
    private String[] getRepositorySubjects(String streamName, String nameWithOwner) {
        // Use wildcard to match ALL events for this repository
        return new String[] { buildSubjectPrefix(streamName, nameWithOwner) + ".>" };
    }

    /**
     * Returns a SINGLE wildcard subject that matches ALL org-level events.
     *
     * <p>Organization events use {@code ?} as repo placeholder in the subject,
     * so we match {@code github.owner.?.>} for all org-level events.
     *
     * @param streamName the NATS stream name ("github" or "gitlab")
     * @param owner      the organization login
     * @return single-element array containing the wildcard subject
     */
    private String[] getOrganizationSubjects(String streamName, String owner) {
        // Use wildcard to match ALL org-level events (? is the repo placeholder)
        return new String[] { buildSubjectPrefix(streamName, owner + "/?") + ".>" };
    }

    /**
     * Returns a SINGLE wildcard subject that matches ALL installation-level events.
     *
     * <p>Installation events use {@code ?/?} as owner/repo placeholder,
     * so we match {@code github.?.?.>} for all installation events.
     * Installation events are GitHub-only (GitLab uses PAT-based auth).
     *
     * @return single-element array containing the wildcard subject
     */
    private String[] getInstallationSubjects() {
        // Use wildcard to match ALL installation-level events (GitHub-only)
        return new String[] { buildSubjectPrefix("github", "?/?") + ".>" };
    }

    /**
     * Builds the NATS subject prefix for a repository identifier.
     * <p>
     * Handles provider-specific namespace conventions:
     * <ul>
     *   <li><b>GitHub:</b> {@code owner/repo} → {@code github.owner.repo}
     *       (always exactly 2 parts)</li>
     *   <li><b>GitLab:</b> {@code group/subgroup/project} → {@code gitlab.group~subgroup.project}
     *       (namespace parts joined with {@code ~}, matching webhook-ingest's gitlab-subject.ts)</li>
     * </ul>
     *
     * @param streamName    the NATS stream name ("github" or "gitlab")
     * @param nameWithOwner repository identifier (e.g., "owner/repo" or "group/sub/project")
     * @return the subject prefix (e.g., "github.owner.repo" or "gitlab.group~sub.project")
     * @throws IllegalArgumentException if nameWithOwner is null, empty, or has invalid format
     */
    static String buildSubjectPrefix(String streamName, String nameWithOwner) {
        if (nameWithOwner == null || nameWithOwner.trim().isEmpty()) {
            throw new IllegalArgumentException("Repository identifier cannot be null or empty.");
        }

        String sanitized = nameWithOwner.replace(".", "~");
        String[] parts = sanitized.split("/");

        if ("gitlab".equals(streamName)) {
            // GitLab: supports nested namespaces (group/subgroup/project)
            // Join all namespace parts with ~ and use last part as project
            if (parts.length < 2) {
                throw new IllegalArgumentException(
                    String.format(
                        "Invalid GitLab repository format: '%s'. Expected 'namespace/project'.",
                        nameWithOwner
                    )
                );
            }
            String namespace = String.join("~", Arrays.copyOfRange(parts, 0, parts.length - 1));
            String project = parts[parts.length - 1];
            return streamName + "." + namespace + "." + project;
        }

        // GitHub: strictly 2-part format (owner/repo)
        if (parts.length != 2) {
            throw new IllegalArgumentException(
                String.format("Invalid repository format: '%s'. Expected format 'owner/repository'.", nameWithOwner)
            );
        }
        return streamName + "." + parts[0] + "." + parts[1];
    }

    private void handleMessage(Message msg) {
        if (shuttingDown.get()) {
            // During shutdown, NAK immediately so another consumer can pick it up
            msg.nak();
            return;
        }

        try {
            String subject = msg.getSubject();
            String eventKey = subject.substring(subject.lastIndexOf('.') + 1);

            // Route to the correct handler registry based on subject prefix
            MessageHandler eventHandler;
            if (subject.startsWith("gitlab.")) {
                eventHandler = gitlabHandlerRegistry.getHandler(eventKey);
            } else {
                eventHandler = githubHandlerRegistry.getHandler(eventKey);
            }

            if (eventHandler == null) {
                // DEBUG level, not WARN: This is expected behavior for events we don't need
                // to handle (e.g., check_run, check_suite, push). The message is still acknowledged
                // so it won't be redelivered. Using WARN would pollute logs and mask real issues.
                log.debug("No handler found for event: eventType={}", eventKey);
                msg.ack();
                return;
            }

            eventHandler.onMessage(msg);
            msg.ack();
        } catch (Exception e) {
            if (!shuttingDown.get()) {
                log.error("Failed to process message: subject={}", sanitizeForLog(msg.getSubject()), e);
            }
            nakWithBackoff(msg);
        }
    }

    /**
     * NAKs a message with exponential backoff delay based on redelivery count.
     * <p>
     * This prevents redelivery storms where a failing message is immediately
     * redelivered hundreds of times. The delay grows exponentially with each
     * retry attempt: ~2s, ~4s, ~8s, ~16s, ~32s, ~64s, up to 5 minutes max.
     * <p>
     * If the message has been redelivered {@link #MAX_REDELIVERY_ATTEMPTS} times,
     * it is acknowledged and logged as a poison message to prevent infinite loops.
     *
     * @param msg the NATS message to NAK
     */
    private void nakWithBackoff(Message msg) {
        try {
            var metadata = msg.metaData();
            long deliveredCount = metadata != null ? metadata.deliveredCount() : 1;

            // Check if this is a poison message that keeps failing
            if (deliveredCount >= MAX_REDELIVERY_ATTEMPTS) {
                log.error(
                    "Poison message detected, giving up after {} attempts: subject={}, streamSeq={}",
                    deliveredCount,
                    sanitizeForLog(msg.getSubject()),
                    metadata != null ? metadata.streamSequence() : "unknown"
                );
                // ACK the message to remove it from the queue - it's not recoverable
                msg.ack();
                return;
            }

            // Calculate exponential backoff based on delivery count
            // deliveredCount is 1-based, so use (deliveredCount - 1) as attempt number
            int attempt = (int) Math.min(deliveredCount - 1, Integer.MAX_VALUE);
            long delayMs = ExponentialBackoff.calculateDelay(attempt, NAK_BASE_DELAY_MS, MAX_NAK_DELAY_MS, 1000);

            log.debug(
                "NAKing message with backoff: subject={}, attempt={}, delayMs={}",
                sanitizeForLog(msg.getSubject()),
                deliveredCount,
                delayMs
            );

            msg.nakWithDelay(Duration.ofMillis(delayMs));
        } catch (Exception e) {
            // If we can't NAK with delay (e.g., connection closed), fall back to immediate NAK
            log.debug("Failed to NAK with delay, using immediate NAK: error={}", e.getMessage());
            try {
                msg.nak();
            } catch (Exception nakError) {
                // Message will be redelivered after ack timeout - nothing more we can do
                log.trace("Failed to NAK message: error={}", nakError.getMessage());
            }
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

            log.info("Reconnecting to NATS server: server={}", natsProperties.server());
            try {
                natsConnection = Nats.connect(buildNatsOptions());
                log.info("Connected to NATS: server={}", natsProperties.server());
            } catch (IOException e) {
                log.error("Failed to connect to NATS: server={}", natsProperties.server(), e);
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
        if (!natsProperties.enabled()) {
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
                log.info("Closed NATS connection: server={}", natsProperties.server());
            } catch (Exception e) {
                log.debug("Failed to close NATS connection: server={}", natsProperties.server(), e);
            }
            natsConnection = null;
        }

        log.info("Completed NATS consumer shutdown: server={}", natsProperties.server());
    }

    /**
     * Tracks heartbeat alarm state for a consumer to detect stuck consumers.
     *
     * @param consecutiveAlarms count of consecutive heartbeat failures without progress
     * @param lastLogTime last time we logged a heartbeat alarm (to avoid log spam)
     * @param lastStreamSeq last observed stream sequence to detect progress
     */
    private record HeartbeatAlarmState(int consecutiveAlarms, Instant lastLogTime, long lastStreamSeq) {
        /** Creates initial state for a consumer. */
        static HeartbeatAlarmState initial(long streamSeq) {
            return new HeartbeatAlarmState(1, Instant.now(), streamSeq);
        }

        /** Increments alarm counter when no progress is made. */
        HeartbeatAlarmState incrementAlarm(Instant logTime) {
            return new HeartbeatAlarmState(consecutiveAlarms + 1, logTime, lastStreamSeq);
        }

        /** Resets alarm counter when progress is detected. */
        HeartbeatAlarmState reset(long newStreamSeq) {
            return new HeartbeatAlarmState(0, lastLogTime, newStreamSeq);
        }
    }

    /**
     * Handles a heartbeat alarm for a consumer.
     * Tracks consecutive alarms and triggers restart if threshold is exceeded.
     *
     * @param consumerName the name of the consumer
     * @param streamSeq the current stream sequence
     */
    private void handleHeartbeatAlarm(String consumerName, long streamSeq) {
        if (consumerName == null || shuttingDown.get()) {
            return;
        }

        HeartbeatAlarmState newState = heartbeatAlarmStates.compute(consumerName, (name, currentState) -> {
            if (currentState == null) {
                // First alarm for this consumer
                return HeartbeatAlarmState.initial(streamSeq);
            }

            // Check if consumer is making progress (stream sequence advanced)
            if (streamSeq > currentState.lastStreamSeq()) {
                log.debug(
                    "Consumer making progress, resetting alarm state: consumerName={}, oldSeq={}, newSeq={}",
                    consumerName,
                    currentState.lastStreamSeq(),
                    streamSeq
                );
                return currentState.reset(streamSeq);
            }

            // No progress - check if we should log
            Instant now = Instant.now();
            Duration sinceLastLog = Duration.between(currentState.lastLogTime(), now);
            boolean shouldLog = sinceLastLog.compareTo(natsProperties.consumer().heartbeatLogInterval()) >= 0;

            if (shouldLog) {
                log.warn(
                    "NATS heartbeat alarm: consumerName={}, consecutiveAlarms={}, streamSeq={}, threshold={}",
                    consumerName,
                    currentState.consecutiveAlarms() + 1,
                    streamSeq,
                    natsProperties.consumer().heartbeatRestartThreshold()
                );
                return currentState.incrementAlarm(now);
            } else {
                // Increment alarm count but don't update log time
                return new HeartbeatAlarmState(
                    currentState.consecutiveAlarms() + 1,
                    currentState.lastLogTime(),
                    currentState.lastStreamSeq()
                );
            }
        });

        // Check if we need to restart the consumer
        if (newState.consecutiveAlarms() >= natsProperties.consumer().heartbeatRestartThreshold()) {
            log.warn(
                "Heartbeat alarm threshold exceeded, triggering consumer restart: consumerName={}, alarms={}",
                consumerName,
                newState.consecutiveAlarms()
            );

            // Reset the alarm state before restart to prevent repeated restart attempts
            heartbeatAlarmStates.remove(consumerName);

            // Find and restart the consumer
            triggerConsumerRestart(consumerName);
        }
    }

    /**
     * Triggers a consumer restart based on the consumer name.
     * Determines if it's a scope consumer or installation consumer and restarts accordingly.
     *
     * @param consumerName the name of the consumer to restart
     */
    private void triggerConsumerRestart(String consumerName) {
        if (consumerName == null) {
            return;
        }

        // Check if it's the installation consumer
        String installationConsumerName = natsProperties.durableConsumerName() + "-installation";
        if (consumerName.equals(installationConsumerName)) {
            restartInstallationConsumer();
            return;
        }

        // Check if it's a scope consumer (format: {durableConsumerName}-scope-{scopeId})
        String scopePrefix = natsProperties.durableConsumerName() + "-scope-";
        if (consumerName.startsWith(scopePrefix)) {
            try {
                Long scopeId = Long.parseLong(consumerName.substring(scopePrefix.length()));
                restartScopeConsumer(scopeId);
            } catch (NumberFormatException e) {
                log.error("Failed to parse scope ID from consumer name: consumerName={}", consumerName);
            }
        }
    }

    /**
     * Restarts a scope consumer by stopping the existing one and creating a new one.
     *
     * @param scopeId the scope ID whose consumer should be restarted
     */
    private void restartScopeConsumer(Long scopeId) {
        if (scopeId == null || shuttingDown.get()) {
            return;
        }

        log.info("Restarting scope consumer: scopeId={}", scopeId);

        ScopeConsumer existing = scopeConsumers.remove(scopeId);
        if (existing != null) {
            try {
                existing.stop();
                cleanupConsumer(existing.streamName, existing.consumerName);
            } catch (Exception e) {
                log.warn("Failed to stop existing consumer during restart: scopeId={}", scopeId, e);
            }
        }

        // Recreate the consumer
        startConsumingScope(scopeId);
        log.info("Scope consumer restart initiated: scopeId={}", scopeId);
    }

    /**
     * Restarts the installation consumer.
     */
    private void restartInstallationConsumer() {
        if (shuttingDown.get()) {
            return;
        }

        log.info("Restarting installation consumer");

        ScopeConsumer existing = installationConsumer;
        if (existing != null) {
            installationConsumer = null;
            try {
                existing.stop();
                cleanupConsumer(existing.streamName, existing.consumerName);
            } catch (Exception e) {
                log.warn("Failed to stop existing installation consumer during restart", e);
            }
        }

        // Recreate the consumer
        startInstallationConsumer();
        log.info("Installation consumer restart initiated");
    }

    /**
     * Non-static error listener that can access service methods for heartbeat alarm handling.
     */
    private class JetStreamErrorListener implements ErrorListener {

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
            String consumerName = sub != null ? sub.getConsumerName() : null;
            handleHeartbeatAlarm(consumerName, lastStreamSequence);
        }
    }

    /**
     * Builds ConsumeOptions with tuned idle heartbeat for remote NATS connections.
     * <p>
     * <b>Why this matters:</b> NATS JetStream push consumers use idle heartbeats to detect
     * stuck subscriptions. The server sends heartbeats when there's no data. If heartbeats
     * don't arrive in time, the client fires {@code heartbeatAlarm()} in the error listener.
     * <p>
     * The default idle heartbeat (15s) is too aggressive for WAN connections where:
     * <ul>
     *   <li>Network latency can spike during congestion</li>
     *   <li>NATS server may be geographically distant</li>
     *   <li>Cloud networking adds variable latency</li>
     * </ul>
     * <p>
     * <b>NATS client behavior:</b> The Java client calculates idle heartbeat as:
     * {@code min(30000ms, expiresIn * 50%)}. To achieve our desired idle heartbeat,
     * we set {@code expiresIn = max(idleHeartbeat * 2, DEFAULT_EXPIRES_IN)}.
     * <p>
     * With {@code idleHeartbeatSeconds=30}, this gives expiresIn=60s, resulting in
     * idleHeartbeat=30s (capped by MAX_HEARTBEAT_MILLIS). Alarms fire after ~3
     * consecutive missed heartbeats, so roughly 90 seconds without server contact.
     *
     * @return ConsumeOptions configured for reliable remote operation
     */
    private ConsumeOptions buildConsumeOptions() {
        // Calculate expiresIn to achieve desired idle heartbeat
        // NATS formula: idleHeartbeat = min(30000ms, expiresIn * 50%)
        // So: expiresIn = idleHeartbeat * 2 (to get the desired idle heartbeat up to 30s cap)
        long desiredIdleHeartbeatMs = natsProperties.consumer().idleHeartbeat().toMillis();
        long calculatedExpiresIn = desiredIdleHeartbeatMs * 2;
        // Ensure we don't go below the default (30s) as NATS has min validation
        long expiresInMs = Math.max(calculatedExpiresIn, 30_000L);

        return ConsumeOptions.builder().expiresIn(expiresInMs).build();
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
        private final String streamName;
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
            String[] subjects,
            String streamName
        ) {
            this.scopeId = scopeId;
            this.consumerName = consumerName;
            this.context = context;
            this.streamContext = streamContext;
            this.currentSubjects = subjects;
            this.streamName = streamName;
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

            // Subscribe to NATS with tuned heartbeat options for remote connections
            // Using buildConsumeOptions() from enclosing service for consistent configuration
            subscription = context.consume(buildConsumeOptions(), this::enqueueMessage);
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
            // Use tuned heartbeat options for the new subscription
            subscription = context.consume(buildConsumeOptions(), this::enqueueMessage);
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
