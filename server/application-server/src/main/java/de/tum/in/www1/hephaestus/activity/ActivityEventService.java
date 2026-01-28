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
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records activity events with XP.
 *
 * <p>Idempotent via unique constraint on event_key.
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
public class ActivityEventService {

    private static final Logger log = LoggerFactory.getLogger(ActivityEventService.class);

    private final ActivityEventRepository eventRepository;
    private final WorkspaceRepository workspaceRepository;
    private final ExperiencePointProperties xpProperties;
    private final Counter eventsRecordedCounter;
    private final Counter eventsDuplicateCounter;
    private final Counter eventsFailedCounter;
    private final Timer recordTimer;
    private final DistributionSummary xpDistribution;
    private final MeterRegistry meterRegistry;

    // Pre-registered timers per event type to avoid cardinality bomb
    private final ConcurrentHashMap<ActivityEventType, Timer> eventTypeTimers = new ConcurrentHashMap<>();

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
        this.recordTimer = Timer.builder("activity.events.record.duration")
            .description("Time to persist activity event")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        this.xpDistribution = DistributionSummary.builder("activity.xp.distribution")
            .description("Distribution of XP values recorded")
            .publishPercentiles(0.5, 0.95, 0.99)
            .register(meterRegistry);
        this.meterRegistry = meterRegistry;
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
     * @return true if recorded successfully, false if:
     *         <ul>
     *           <li>Event is a duplicate (already exists with same event_key)</li>
     *           <li>Workspace not found (logs warning, does not throw)</li>
     *         </ul>
     */
    @Transactional
    @Observed(name = "activity.record", contextualName = "record-activity-event")
    public boolean record(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        @Nullable User actor,
        @Nullable Repository repository,
        ActivityTargetType targetType,
        Long targetId,
        double xp
    ) {
        Workspace workspace = workspaceRepository.findById(workspaceId).orElse(null);
        if (workspace == null) {
            eventsFailedCounter.increment();
            log.warn(
                "Failed to record event, workspace not found: scopeId={}, eventType={}, targetId={}",
                workspaceId,
                eventType,
                targetId
            );
            return false;
        }

        // Clamp XP to valid bounds: minimum 0, configurable maximum (safety cap)
        double maxXp = xpProperties.maxXpPerEvent();
        double clampedXp = Math.max(0.0, Math.min(xp, maxXp));
        if (clampedXp != xp) {
            log.debug("Clamped XP value: originalXp={}, clampedXp={}, eventType={}", xp, clampedXp, eventType);
        }

        // Round to 2 decimal places for consistent precision (HALF_UP rounding)
        double roundedXp = XpPrecision.round(clampedXp);

        String eventKey = ActivityEvent.buildKey(eventType, targetId, occurredAt);

        // Check for duplicate BEFORE attempting insert.
        // This is critical because DataIntegrityViolationException is thrown at transaction
        // commit time, not at save() time. When running in batch transactions (e.g., backfill),
        // the exception would propagate past our try-catch and fail the entire batch.
        // By checking first, we avoid the constraint violation entirely.
        if (eventRepository.existsByWorkspaceIdAndEventKey(workspaceId, eventKey)) {
            eventsDuplicateCounter.increment();
            log.debug("Skipped duplicate event (pre-check): eventKey={}", eventKey);
            return false;
        }

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
            .build();

        try {
            // Use pre-registered timers to avoid cardinality explosion
            Timer eventTimer = getTimerForEventType(eventType);
            eventTimer.record(() -> eventRepository.save(event));
            recordTimer.record(() -> {}); // Record overall duration

            eventsRecordedCounter.increment();
            xpDistribution.record(roundedXp);

            // Structured logging with trace context
            log.info(
                "Recorded activity event: eventType={}, targetId={}, xp={}, scopeId={}, actorId={}",
                eventType,
                targetId,
                roundedXp,
                workspaceId,
                actor != null ? actor.getId() : null
            );
            return true;
        } catch (DataIntegrityViolationException e) {
            eventsDuplicateCounter.increment();
            log.debug("Skipped duplicate event: eventKey={}", eventKey);
            return false;
        }
    }

    /**
     * Record an activity event using a command object.
     *
     * <p>This is the preferred API for recording events - cleaner than the
     * parameter method and provides compile-time safety via the builder pattern.
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
            command.xp()
        );
    }

    /**
     * Record an activity event for a deleted entity.
     *
     * <p>When an entity is deleted, we may not have access to the actor or repository
     * information from the entity itself. This method records the deletion event
     * with null actor and repository - it's still valuable for audit trail purposes.
     *
     * <p>Deleted events always have 0 XP since they represent data removal, not
     * value-adding activity.
     *
     * @param workspaceId  the workspace ID
     * @param eventType    the event type (e.g., COMMENT_DELETED, ISSUE_DELETED)
     * @param occurredAt   when the deletion occurred
     * @param targetType   the type of entity that was deleted
     * @param targetId     the ID of the deleted entity
     * @return true if recorded successfully, false otherwise
     */
    @Transactional
    @Observed(name = "activity.record.deleted", contextualName = "record-deleted-activity-event")
    public boolean recordDeleted(
        Long workspaceId,
        ActivityEventType eventType,
        Instant occurredAt,
        ActivityTargetType targetType,
        Long targetId
    ) {
        // Record with null actor and repository - acceptable for deletion audit trail
        return record(
            workspaceId,
            eventType,
            occurredAt,
            null, // actor unknown for deleted entities
            null, // repository unknown for deleted entities
            targetType,
            targetId,
            0.0  // deletions have no XP value
        );
    }
}
