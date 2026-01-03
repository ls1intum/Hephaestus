package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.core.exception.EntityNotFoundException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Service for managing dead letter events.
 *
 * <p>Provides business logic for dead letter operations including retry, discard,
 * and statistics. This separates the data access concerns from the controller layer.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DeadLetterEventService {

    private static final Logger logger = LoggerFactory.getLogger(DeadLetterEventService.class);

    private final DeadLetterEventRepository deadLetterRepository;
    private final ActivityEventService activityEventService;

    /**
     * Finds pending dead letters for retry, ordered by creation time (oldest first).
     *
     * @param limit maximum number of results
     * @return list of pending dead letter events
     */
    public List<DeadLetterEvent> findPendingForRetry(int limit) {
        return deadLetterRepository.findPendingForRetry(limit);
    }

    /**
     * Finds a dead letter event by ID.
     *
     * @param id the dead letter ID
     * @return the dead letter event
     * @throws EntityNotFoundException if not found
     */
    public DeadLetterEvent findById(UUID id) {
        return deadLetterRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("DeadLetterEvent", id.toString()));
    }

    /**
     * Discards a dead letter with a reason.
     *
     * @param id the dead letter ID
     * @param reason the discard reason
     * @throws EntityNotFoundException if not found
     */
    @Transactional
    public void discard(UUID id, String reason) {
        DeadLetterEvent event = findById(id);
        logger.info("Discarding dead letter {} with reason: {}", id, reason);
        event.markDiscarded(reason);
        deadLetterRepository.save(event);
    }

    /**
     * Retries a dead letter event by re-recording the activity.
     *
     * <p>Increments the retry count on failure to track attempts for
     * eventual auto-discard after max retries.
     *
     * @param id the dead letter ID
     * @return result containing success status and message
     * @throws EntityNotFoundException if not found
     */
    @Transactional
    public RetryResult retry(UUID id) {
        DeadLetterEvent event = findById(id);

        if (event.getStatus() != DeadLetterEvent.Status.PENDING) {
            return new RetryResult(false, "Dead letter is not in PENDING status");
        }

        logger.info("Retrying dead letter {}", id);

        try {
            boolean success = activityEventService.record(
                event.getWorkspaceId(),
                event.getEventType(),
                event.getOccurredAt(),
                null, // Actor reference not preserved in dead letter
                null, // Repository reference not preserved in dead letter
                ActivityTargetType.fromValue(event.getTargetType()),
                event.getTargetId(),
                event.getXp(),
                SourceSystem.fromValue(event.getSourceSystem()),
                null // Payload not preserved
            );

            if (success) {
                event.markResolved("Retry successful");
                deadLetterRepository.save(event);
                logger.info("Dead letter {} successfully retried", id);
                return new RetryResult(true, "Event successfully recorded");
            } else {
                // Duplicate means it was already recorded - treat as success
                event.markResolved("Already recorded (duplicate)");
                deadLetterRepository.save(event);
                return new RetryResult(true, "Event was already recorded");
            }
        } catch (Exception e) {
            // Increment retry count on failure
            int newCount = event.incrementRetryCount();
            deadLetterRepository.save(event);
            logger.error("Failed to retry dead letter {}: {} (attempt {})", id, e.getMessage(), newCount);
            return new RetryResult(false, "Retry failed: " + e.getMessage());
        }
    }

    /**
     * Gets statistics about dead letters.
     *
     * @return statistics including counts by status and event type
     */
    public DeadLetterStats getStats() {
        long pending = deadLetterRepository.countByStatus(DeadLetterEvent.Status.PENDING);
        long resolved = deadLetterRepository.countByStatus(DeadLetterEvent.Status.RESOLVED);
        long discarded = deadLetterRepository.countByStatus(DeadLetterEvent.Status.DISCARDED);

        List<Object[]> byTypeRaw = deadLetterRepository.countPendingByEventType();
        Map<String, Long> byType = byTypeRaw
            .stream()
            .collect(Collectors.toMap(row -> ((ActivityEventType) row[0]).name(), row -> (Long) row[1]));

        return new DeadLetterStats(pending, resolved, discarded, byType);
    }

    /**
     * Result of a retry operation.
     */
    public record RetryResult(boolean success, String message) {}

    /**
     * Statistics about dead letter events.
     */
    public record DeadLetterStats(long pending, long resolved, long discarded, Map<String, Long> byEventType) {}
}
