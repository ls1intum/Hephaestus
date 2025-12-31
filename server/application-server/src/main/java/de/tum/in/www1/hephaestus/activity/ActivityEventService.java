package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.workspace.Workspace;
import de.tum.in.www1.hephaestus.workspace.WorkspaceRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Instant;
import java.util.Map;
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
 */
@Service
public class ActivityEventService {

    private static final Logger logger = LoggerFactory.getLogger(ActivityEventService.class);

    private final ActivityEventRepository eventRepository;
    private final WorkspaceRepository workspaceRepository;
    private final Counter eventsRecordedCounter;
    private final Counter eventsDuplicateCounter;

    public ActivityEventService(
        ActivityEventRepository eventRepository,
        WorkspaceRepository workspaceRepository,
        MeterRegistry meterRegistry
    ) {
        this.eventRepository = eventRepository;
        this.workspaceRepository = workspaceRepository;
        this.eventsRecordedCounter = Counter.builder("activity.events.recorded")
            .description("Number of activity events recorded")
            .register(meterRegistry);
        this.eventsDuplicateCounter = Counter.builder("activity.events.duplicate")
            .description("Number of duplicate activity events skipped")
            .register(meterRegistry);
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
            logger.warn("Workspace not found: workspaceId={}", workspaceId);
            return false;
        }

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
            .xp(xp)
            .sourceSystem(sourceSystem.getValue())
            .payload(payload)
            .build();

        try {
            eventRepository.save(event);
            eventsRecordedCounter.increment();
            logger.debug("Recorded: {} {} xp={}", eventType, targetId, xp);
            return true;
        } catch (DataIntegrityViolationException e) {
            eventsDuplicateCounter.increment();
            logger.debug("Duplicate skipped: {}", eventKey);
            return false;
        }
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
}
