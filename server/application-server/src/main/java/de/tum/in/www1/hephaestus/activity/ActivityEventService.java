package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.scoring.ExperiencePointProperties;
import de.tum.in.www1.hephaestus.activity.scoring.XpPrecision;
import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CacheEvict;
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

    private final ActivityEventRepository eventRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ExperiencePointProperties xpProperties;
    private final Counter eventsRecordedCounter;
    private final Counter eventsDuplicateCounter;
    private final Counter eventsFailedCounter;
    private final MeterRegistry meterRegistry;

    public ActivityEventService(
        ActivityEventRepository eventRepository,
        WorkspaceRepository workspaceRepository,
        ExperiencePointProperties xpProperties,
        MeterRegistry meterRegistry
    ) {
        this.eventRepository = eventRepository;
        this.workspaceRepository = workspaceRepository;
        this.xpProperties = xpProperties;
        this.eventsRecordedCounter = Counter.builder("activity.events.recorded")
            .description("Number of activity events recorded")
            .register(meterRegistry);
        this.eventsDuplicateCounter = Counter.builder("activity.events.duplicate")
            .description("Number of duplicate activity events skipped")
            .register(meterRegistry);
        this.eventsFailedCounter = Counter.builder("activity.events.failed")
            .description("Number of activity events that failed to record after retries")
            .register(meterRegistry);
        this.meterRegistry = meterRegistry;
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
    @CacheEvict(value = "leaderboardXp", allEntries = true)
    @Retryable(
        retryFor = TransientDataAccessException.class,
        maxAttempts = 3,
        backoff = @Backoff(delay = 100, multiplier = 2, maxDelay = 1000)
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
            .build();

        try {
            Timer.builder("activity.events.record.duration")
                .description("Time to persist activity event")
                .tag("eventType", eventType.name())
                .register(meterRegistry)
                .record(() -> eventRepository.save(event));
            eventsRecordedCounter.increment();
            logger.debug("Recorded: {} {} xp={}", eventType, targetId, roundedXp);
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

        // Future: Add migration logic when schema evolves
        // Example for v2:
        // if (event.getSchemaVersion() < 2) {
        //     event = migrateV1ToV2(event);
        // }

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
        return record(
            command.workspaceId(),
            command.eventType(),
            command.occurredAt(),
            command.actor(),
            command.repository(),
            command.targetType(),
            command.targetId(),
            command.xp(),
            command.sourceSystem(),
            command.payload()
        );
    }

    /**
     * Recovery method called when all retry attempts are exhausted.
     * This method signature must match the retryable method parameters.
     *
     * <p><strong>Dead Letter Handling:</strong> Failed events are logged with full context
     * for manual investigation. Consider adding a dead letter table for persistence
     * if automated retry is needed.
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
        // Log with structured context for dead letter investigation
        logger.error(
            "DEAD_LETTER: Failed to record activity event after retries. " +
            "workspaceId={}, eventType={}, targetId={}, xp={}, source={}, error={}",
            workspaceId,
            eventType,
            targetId,
            xp,
            sourceSystem,
            e.getMessage(),
            e
        );
        return false;
    }
}
