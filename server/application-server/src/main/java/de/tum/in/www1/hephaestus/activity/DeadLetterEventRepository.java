package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for dead letter events - activity events that failed to record.
 *
 * <p>Provides queries for investigation, retry, and cleanup workflows.
 */
@Repository
@WorkspaceAgnostic("System-wide debugging and recovery - dead letters span all workspaces")
public interface DeadLetterEventRepository extends JpaRepository<DeadLetterEvent, UUID> {
    /**
     * Find all pending dead letters for a workspace, ordered by creation time.
     *
     * @param workspaceId the workspace to query
     * @return pending dead letters, oldest first
     */
    List<DeadLetterEvent> findByWorkspaceIdAndStatusOrderByCreatedAtAsc(
        Long workspaceId,
        DeadLetterEvent.Status status
    );

    /**
     * Find pending dead letters for retry, limited to avoid overwhelming the system.
     *
     * @param status the status to filter by
     * @param limit maximum number to return
     * @return dead letters matching status, oldest first
     */
    @Query(
        """
        SELECT d FROM DeadLetterEvent d
        WHERE d.status = :status
        ORDER BY d.createdAt ASC
        LIMIT :limit
        """
    )
    List<DeadLetterEvent> findByStatusOrderByCreatedAtAscLimit(
        @Param("status") DeadLetterEvent.Status status,
        @Param("limit") int limit
    );

    /**
     * Find pending dead letters for retry (convenience method).
     *
     * @param limit maximum number to return
     * @return pending dead letters eligible for retry
     */
    default List<DeadLetterEvent> findPendingForRetry(int limit) {
        return findByStatusOrderByCreatedAtAscLimit(DeadLetterEvent.Status.PENDING, limit);
    }

    /**
     * Count dead letters by event type for monitoring dashboards.
     *
     * @param status the status to filter by
     * @return count of events grouped by type
     */
    @Query(
        """
        SELECT d.eventType, COUNT(d)
        FROM DeadLetterEvent d
        WHERE d.status = :status
        GROUP BY d.eventType
        """
    )
    List<Object[]> countByStatusGroupByEventType(@Param("status") DeadLetterEvent.Status status);

    /**
     * Count pending dead letters by event type (convenience method).
     *
     * @return count of pending events grouped by type
     */
    default List<Object[]> countPendingByEventType() {
        return countByStatusGroupByEventType(DeadLetterEvent.Status.PENDING);
    }

    /**
     * Count total pending dead letters for alerting.
     *
     * @return number of unresolved dead letters
     */
    long countByStatus(DeadLetterEvent.Status status);

    /**
     * Delete dead letters by statuses older than the retention period.
     *
     * @param statuses set of statuses to delete
     * @param cutoff events resolved before this time will be deleted
     * @return number of deleted records
     */
    @Modifying
    @Query(
        """
        DELETE FROM DeadLetterEvent d
        WHERE d.status IN :statuses
        AND d.resolvedAt < :cutoff
        """
    )
    int deleteByStatusInAndResolvedAtBefore(
        @Param("statuses") Set<DeadLetterEvent.Status> statuses,
        @Param("cutoff") Instant cutoff
    );

    /**
     * Delete resolved/discarded dead letters older than the retention period (convenience method).
     *
     * @param cutoff events resolved before this time will be deleted
     * @return number of deleted records
     */
    default int deleteResolvedBefore(Instant cutoff) {
        return deleteByStatusInAndResolvedAtBefore(
            Set.of(DeadLetterEvent.Status.RESOLVED, DeadLetterEvent.Status.DISCARDED),
            cutoff
        );
    }

    /**
     * Find dead letters by error type for pattern analysis.
     *
     * @param errorType the exception class name
     * @return matching dead letters
     */
    List<DeadLetterEvent> findByErrorTypeAndStatusOrderByCreatedAtDesc(String errorType, DeadLetterEvent.Status status);
}
