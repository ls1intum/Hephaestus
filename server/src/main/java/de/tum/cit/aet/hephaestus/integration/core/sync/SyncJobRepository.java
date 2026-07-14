package de.tum.cit.aet.hephaestus.integration.core.sync;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SyncJobRepository extends JpaRepository<SyncJob, Long> {
    Optional<SyncJob> findByIdAndWorkspace_Id(Long id, long workspaceId);

    /** Job history page. Callers supply the sort (see {@code SyncStatusService}, {@code createdAt} desc). */
    Page<SyncJob> findByConnection_IdAndWorkspace_Id(long connectionId, long workspaceId, Pageable pageable);

    /** Every sync job across a workspace's connections — the workspace-wide alternative to the per-connection page above. */
    Page<SyncJob> findByWorkspace_Id(long workspaceId, Pageable pageable);

    /** The one-active-job guard's read: present result means the connection already has a job in flight. */
    Optional<SyncJob> findFirstByConnection_IdAndStatusInOrderByCreatedAtDesc(
        long connectionId,
        Collection<SyncJobStatus> statuses
    );

    /** Most recent job that has actually finished — the source for connection health derivation. */
    Optional<SyncJob> findFirstByConnection_IdAndStatusInOrderByFinishedAtDesc(
        long connectionId,
        Collection<SyncJobStatus> statuses
    );

    /**
     * Zombie sweep across every workspace: {@code PENDING}/{@code RUNNING} jobs whose lease
     * (heartbeat, falling back to creation time before the first heartbeat lands) is stale.
     */
    @WorkspaceAgnostic("Cross-workspace zombie sweep; caller is the @ConditionalOnServerRole sweeper")
    @Query(
        "SELECT j FROM SyncJob j WHERE j.status IN :statuses " + "AND COALESCE(j.heartbeatAt, j.createdAt) < :cutoff"
    )
    List<SyncJob> findAbandoned(@Param("statuses") Collection<SyncJobStatus> statuses, @Param("cutoff") Instant cutoff);

    /**
     * Same as {@link #findAbandoned} but scoped to one connection — run inline before the
     * one-active-job guard rejects a manual trigger, so a crashed prior run never blocks
     * "Sync now" for the full sweep interval.
     */
    @WorkspaceAgnostic("ID-based inline reap; connectionId comes from a workspace-scoped caller")
    @Query(
        "SELECT j FROM SyncJob j WHERE j.connection.id = :connectionId AND j.status IN :statuses " +
            "AND COALESCE(j.heartbeatAt, j.createdAt) < :cutoff"
    )
    List<SyncJob> findAbandonedForConnection(
        @Param("connectionId") long connectionId,
        @Param("statuses") Collection<SyncJobStatus> statuses,
        @Param("cutoff") Instant cutoff
    );

    /** Bulk lease touch for every currently-registered in-JVM handle (the 60s heartbeat scheduler). */
    @WorkspaceAgnostic("ID-based bulk heartbeat; ids come from the in-process job handle registry")
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE SyncJob j SET j.heartbeatAt = :now WHERE j.id IN :ids")
    int touchHeartbeat(@Param("ids") Collection<Long> ids, @Param("now") Instant now);

    /** Cancel-flag projection, read back on the same heartbeat pass so a cancel request reaches the runner. */
    interface CancelFlagProjection {
        Long getId();
        boolean isCancelRequested();
    }

    @WorkspaceAgnostic("ID-based cancel-flag refresh; ids come from the in-process job handle registry")
    @Query("SELECT j.id AS id, j.cancelRequested AS cancelRequested FROM SyncJob j WHERE j.id IN :ids")
    List<CancelFlagProjection> findCancelFlags(@Param("ids") Collection<Long> ids);

    /**
     * Retention: keep only the newest {@code limit} rows per connection, pruning the rest right
     * after insert. This is a live-operations view, not an audit trail — no separate sweeper needed.
     */
    @WorkspaceAgnostic(
        "ID-based retention prune; connectionId comes from the job just created in a workspace-scoped call"
    )
    @Modifying
    @Query(
        value = "DELETE FROM sync_job WHERE connection_id = :connectionId AND id NOT IN " +
            "(SELECT id FROM sync_job WHERE connection_id = :connectionId ORDER BY created_at DESC LIMIT :limit)",
        nativeQuery = true
    )
    int pruneOldJobs(@Param("connectionId") long connectionId, @Param("limit") int limit);
}
