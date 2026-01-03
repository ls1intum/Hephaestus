package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointProperties;
import de.tum.in.www1.hephaestus.activity.scoring.XpPrecision;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.annotation.Observed;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.lang.Nullable;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records activity events with XP.
 *
 * <p>Idempotent via unique constraint on event_key.
 * Retries transient database errors up to 3 times with exponential backoff.
 * Evicts leaderboard cache on successful writes to ensure freshness.
 *
 * <h3>Security Model</h3>
 * <p><strong>INTERNAL API - NOT FOR DIRECT CONTROLLER USE.</strong>
 *
 * <p>This service has no authorization checks because it is designed to be called
 * exclusively by {@link ActivityEventListener} in response to domain events that
 * have already been authenticated and authorized through the webhook/sync pipeline.
 *
 * <p><strong>Do NOT expose this service to controllers or REST endpoints.</strong>
 * All event recording flows through the listener pattern:
 * <pre>
 * Authenticated Webhook → MessageHandler → DomainEvent → ActivityEventListener → ActivityEventService
 * </pre>
 *
 * <p>If authorization is needed in the future (e.g., manual event injection),
 * add {@code @PreAuthorize("hasRole('ADMIN')")} to the record() method.
 *
 * @see ActivityEventListener The only intended caller of this service
 */
@Service
class ActivityEventService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityEventService.class);

    /**
     * Current schema version for activity events.
     *
     * <p><strong>Version history:</strong>
     * <ul>
     *   <li>v1 (initial): Base event structure with XP scoring</li>
     * </ul>
     *
     * <p>Increment this when making breaking changes to event structure,
     * XP formulas, or payload format. Add migration logic in
     * {@link #migrateToCurrentVersion(ActivityEvent)}.
     */
    public static final int CURRENT_SCHEMA_VERSION = 1;

    private static final int MAX_STACK_TRACE_LENGTH = 4000;
    private static final int MAX_RETRY_ATTEMPTS = 3;

    private final ActivityEventRepository eventRepository;
    private final DeadLetterEventRepository deadLetterRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ExperiencePointProperties xpProperties;
    private final LeaderboardCacheManager cacheManager;
    private final Counter eventsRecordedCounter;
    private final Counter eventsDuplicateCounter;
    private final Counter eventsFailedCounter;
    private final Counter deadLettersPersistedCounter;
    private final Timer recordTimer;
    private final DistributionSummary xpDistribution;
    private final MeterRegistry meterRegistry;

    // Pre-registered timers per event type to avoid cardinality bomb
    private final ConcurrentHashMap<ActivityEventType, Timer> eventTypeTimers = new ConcurrentHashMap<>();

    public ActivityEventService(
        ActivityEventRepository eventRepository,
        DeadLetterEventRepository deadLetterRepository,
        WorkspaceRepository workspaceRepository,
        ExperiencePointProperties xpProperties,
        LeaderboardCacheManager cacheManager,
        MeterRegistry meterRegistry
    ) {
        this.eventRepository = eventRepository;
        this.deadLetterRepository = deadLetterRepository;
        this.workspaceRepository = workspaceRepository;
        this.xpProperties = xpProperties;
        this.cacheManager = cacheManager;
        this.eventsRecordedCounter = Counter.builder("activity.events.recorded")
            .description("Number of activity events recorded")
            .register(meterRegistry);
        this.eventsDuplicateCounter = Counter.builder("activity.events.duplicate")
            .description("Number of duplicate activity events skipped")
            .register(meterRegistry);
        this.eventsFailedCounter = Counter.builder("activity.events.failed")
            .description("Number of activity events that failed to record after retries")
            .register(meterRegistry);
        this.deadLettersPersistedCounter = Counter.builder("activity.dead_letters.persisted")
            .description("Number of failed events persisted to dead letter storage")
            .register(meterRegistry);
        this.recordTimer = Timer.builder("activity.events.record.duration")
            .description("Time to persist activity event")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        this.xpDistribution = DistributionSummary.builder("activity.xp.distribution")
            .description("Distribution of XP values recorded")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        this.meterRegistry = meterRegistry;

        // Register gauge for pending dead letters
        deadLetterRepository.count(); // Initialize lazy count
        meterRegistry.gauge("activity.dead_letters.pending", deadLetterRepository, repo ->
            repo.countByStatus(DeadLetterEvent.Status.PENDING)
        );
    }

    /**
     * Get or create a timer for the given event type.
     * Pre-registers timers to avoid unbounded cardinality.
     */
    private Timer getTimerForEventType(ActivityEventType eventType) {
        return eventTypeTimers.computeIfAbsent(eventType, type ->
            Timer.builder("activity.events.record.duration.by_type")
                .description("Time to persist activity event by type")
                .tag("eventType", type.name())
                .publishPercentiles(0.5, 0.95, 0.99)
                .register(meterRegistry)
        );
    }

    /**
     * Record an activity event.
     *
     * <p>This method is idempotent: duplicate events (same event_key) are silently ignored.
     *
     * <p>Retries up to 3 times on transient database errors with exponential backoff.
     *
     * <p>Evicts the leaderboard cache on successful writes to ensure real-time accuracy.
     *
     * @return true if recorded successfully, false if:
     *         <ul>
     *           <li>Event is a duplicate (already exists with same event_key)</li>
     *           <li>Workspace not found (logs warning, does not throw)</li>
     *           <li>All retry attempts exhausted (logs error)</li>
     *         </ul>
     */
    @Transactional
    @Observed(name = "activity.record", contextualName = "record-activity-event")
    @Retryable(
        retryFor = TransientDataAccessException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000),
        listeners = "activityRetryListener"
    )
    public boolean record(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        @Nullable User actor,
        @Nullable Repository repository,
        ActivityTargetType targetType,
        Long targetId,
        double xp,
        SourceSystem sourceSystem,
        @Nullable Map<String, Object> payload
    ) {
        return recordWithContext(
            workspaceId,
            eventType,
            occurredAt,
            actor,
            repository,
            targetType,
            targetId,
            xp,
            sourceSystem,
            payload,
            "webhook"
        );
    }

    /**
     * Record an activity event with explicit trigger context.
     *
     * <p>Use this method when you need to specify why the event was recorded
     * (e.g., "sync", "backfill", "scheduled", "manual").
     */
    @Transactional
    public boolean recordWithContext(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        @Nullable User actor,
        @Nullable Repository repository,
        ActivityTargetType targetType,
        Long targetId,
        double xp,
        SourceSystem sourceSystem,
        @Nullable Map<String, Object> payload,
        @Nullable String triggerContext
    ) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            eventsFailedCounter.increment();
            logger.warn(
                "Failed to record event: workspace not found. workspaceId={}, eventType={}, targetId={}",
                workspaceId,
                eventType,
                targetId
            );
            return false;
        }

        // Clamp XP to valid bounds: minimum 0, configurable maximum (safety cap)
        double maxXp = xpProperties.getMaxXpPerEvent();
        double clampedXp = Math.max(0.0, Math.min(xp, maxXp));
        if (clampedXp != xp) {
            logger.debug("Clamped XP from {} to {} for event type {}", xp, clampedXp, eventType);
        }

        // Round to 2 decimal places for consistent precision (HALF_UP rounding)
        double roundedXp = XpPrecision.round(clampedXp);

        String eventKey = ActivityEvent.buildKey(eventType, targetId, occurredAt);

        ActivityEvent event = ActivityEvent.builder()
            .eventKey(eventKey)
            .eventType(eventType)
            .occurredAt(occurredAt)
            .actor(actor)
            .workspace(workspace)
            .repository(repository)
            .targetType(targetType.getValue())
            .targetId(targetId)
            .xp(roundedXp)
            .sourceSystem(sourceSystem.getValue())
            .payload(payload)
            .schemaVersion(CURRENT_SCHEMA_VERSION)
            .triggerContext(triggerContext != null ? triggerContext : "webhook")
            .build();

        try {
            // Use pre-registered timers to avoid cardinality explosion
            Timer eventTimer = getTimerForEventType(eventType);
            eventTimer.record(() -> eventRepository.save(event));
            recordTimer.record(() -> {}); // Record overall duration

            eventsRecordedCounter.increment();
            xpDistribution.record(roundedXp);

            // Evict cache only for this workspace (granular invalidation)
            cacheManager.evictWorkspace(workspaceId);

            // Structured logging with trace context
            logger.info(
                "activity.event.recorded eventType={} targetId={} xp={} workspaceId={} actorId={}",
                eventType,
                targetId,
                roundedXp,
                workspaceId,
                actor != null ? actor.getId() : null
            );
            return true;
        } catch (DataIntegrityViolationException e) {
            eventsDuplicateCounter.increment();
            logger.debug("Duplicate skipped: {}", eventKey);
            return false;
        }
    }

    /**
     * Migrate an event from an older schema version to the current version.
     *
     * <p>Use this when processing historical events that may have been recorded
     * with an older schema version. The method handles all necessary transformations
     * to bring the event up to date.
     *
     * <p><strong>Current migrations:</strong> None (v1 is the initial version).
     *
     * @param event the event to migrate (may be from any schema version)
     * @return the migrated event at {@link #CURRENT_SCHEMA_VERSION}
     */
    public ActivityEvent migrateToCurrentVersion(ActivityEvent event) {
        if (event.getSchemaVersion() == CURRENT_SCHEMA_VERSION) {
            return event;
        }

        logger.debug(
            "Migrated event {} from schema v{} to v{}",
            event.getEventKey(),
            event.getSchemaVersion(),
            CURRENT_SCHEMA_VERSION
        );

        return event;
    }

    /**
     * Convenience overload without payload.
     */
    public boolean record(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        @Nullable User actor,
        @Nullable Repository repository,
        ActivityTargetType targetType,
        Long targetId,
        double xp,
        SourceSystem sourceSystem
    ) {
        return record(
            workspaceId,
            eventType,
            occurredAt,
            actor,
            repository,
            targetType,
            targetId,
            xp,
            sourceSystem,
            null
        );
    }

    /**
     * Record an activity event using a command object.
     *
     * <p>This is the preferred API for recording events - cleaner than the
     * 10-parameter method and provides compile-time safety via the builder pattern.
     *
     * @param command the command containing all event data
     * @return true if recorded successfully, false otherwise
     * @see RecordActivityCommand
     */
    public boolean record(RecordActivityCommand command) {
        return recordWithContext(
            command.workspaceId(),
            command.eventType(),
            command.occurredAt(),
            command.actor(),
            command.repository(),
            command.targetType(),
            command.targetId(),
            command.xp(),
            command.sourceSystem(),
            command.payload(),
            command.triggerContext()
        );
    }

    /**
     * Recovery method called when all retry attempts are exhausted.
     * This method signature must match the retryable method parameters.
     *
     * <p><strong>Dead Letter Handling:</strong> Failed events are persisted to the
     * {@code dead_letter_event} table for later investigation and potential retry.
     * This ensures no event data is lost even when the primary write path fails.
     *
     * <p><strong>Monitoring:</strong> Dead letters can be monitored via:
     * <ul>
     *   <li>{@code activity.events.failed} - Counter for alerting</li>
     *   <li>{@code activity.dead_letters.persisted} - Counter for dead letter persistence</li>
     *   <li>Query the {@code dead_letter_event} table for investigation</li>
     * </ul>
     *
     * @see DeadLetterEvent
     * @see DeadLetterEventRepository
     */
    @Recover
    public boolean recoverFromTransientError(
        TransientDataAccessException e,
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        @Nullable User actor,
        @Nullable Repository repository,
        ActivityTargetType targetType,
        Long targetId,
        double xp,
        SourceSystem sourceSystem,
        @Nullable Map<String, Object> payload
    ) {
        eventsFailedCounter.increment();

        // Build deterministic event key for correlation
        String eventKey = ActivityEvent.buildKey(eventType, targetId, occurredAt);

        // Extract stack trace (truncated to avoid bloat)
        String stackTrace = truncateStackTrace(e);

        // Use MDC for structured logging context
        try (
            var ignored = MDC.putCloseable("eventKey", eventKey);
            var ignored2 = MDC.putCloseable("workspaceId", String.valueOf(workspaceId));
            var ignored3 = MDC.putCloseable("eventType", eventType.name())
        ) {
            // Log with full context for immediate alerting
            logger.error(
                "DEAD_LETTER: Failed to record activity event after {} retries. " +
                    "eventKey={}, workspaceId={}, eventType={}, targetType={}, targetId={}, " +
                    "xp={}, source={}, actorId={}, repositoryId={}, errorType={}, error={}",
                MAX_RETRY_ATTEMPTS,
                eventKey,
                workspaceId,
                eventType,
                targetType,
                targetId,
                xp,
                sourceSystem,
                actor != null ? actor.getId() : null,
                repository != null ? repository.getId() : null,
                e.getClass().getName(),
                e.getMessage(),
                e
            );

            // Persist to dead letter storage for later investigation/retry
            persistDeadLetter(
                workspaceId,
                eventType,
                occurredAt,
                actor,
                repository,
                targetType,
                targetId,
                xp,
                sourceSystem,
                payload,
                e,
                stackTrace
            );
        }

        return false;
    }

    /**
     * Persist a failed event to dead letter storage.
     *
     * <p>This method is best-effort: if dead letter persistence itself fails,
     * we log the error but don't throw, since the original failure is already logged.
     */
    private void persistDeadLetter(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        @Nullable User actor,
        @Nullable Repository repository,
        ActivityTargetType targetType,
        Long targetId,
        double xp,
        SourceSystem sourceSystem,
        @Nullable Map<String, Object> payload,
        Exception error,
        String stackTrace
    ) {
        try {
            DeadLetterEvent deadLetter = DeadLetterEvent.builder()
                .workspaceId(workspaceId)
                .eventType(eventType)
                .occurredAt(occurredAt)
                .actorId(actor != null ? actor.getId() : null)
                .repositoryId(repository != null ? repository.getId() : null)
                .targetType(targetType.getValue())
                .targetId(targetId)
                .xp(xp)
                .sourceSystem(sourceSystem.getValue())
                .payload(payload)
                .errorMessage(truncateString(error.getMessage(), 2000))
                .errorType(error.getClass().getName())
                .stackTrace(stackTrace)
                .retryCount(MAX_RETRY_ATTEMPTS)
                .status(DeadLetterEvent.Status.PENDING)
                .build();

            deadLetterRepository.save(deadLetter);
            deadLettersPersistedCounter.increment();

            logger.info(
                "Dead letter persisted for later retry. deadLetterId={}, eventType={}, targetId={}",
                deadLetter.getId(),
                eventType,
                targetId
            );
        } catch (Exception persistError) {
            // Dead letter persistence failed - log but don't throw
            // The original failure is already logged, this is defense-in-depth
            logger.error(
                "CRITICAL: Failed to persist dead letter. Event data may be lost. " +
                    "workspaceId={}, eventType={}, targetId={}, persistError={}",
                workspaceId,
                eventType,
                targetId,
                persistError.getMessage(),
                persistError
            );
        }
    }

    /**
     * Truncate stack trace to avoid database bloat.
     */
    private String truncateStackTrace(Exception e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String fullTrace = sw.toString();
        return truncateString(fullTrace, MAX_STACK_TRACE_LENGTH);
    }

    /**
     * Truncate a string to the specified maximum length.
     */
    private String truncateString(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength - 3) + "...";
    }
}
