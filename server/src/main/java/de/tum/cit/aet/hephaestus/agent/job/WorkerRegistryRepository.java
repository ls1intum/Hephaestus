package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Persistence for the self-reported worker liveness registry (#1138). Written by each worker
 * ({@code WorkerLivenessReporter}); read by the orphan-recovery sweep to find jobs whose owning
 * worker has gone stale.
 */
@Repository
@WorkspaceAgnostic("Fleet-wide worker coordination; not workspace-scoped.")
public interface WorkerRegistryRepository extends JpaRepository<WorkerRegistry, String> {
    /**
     * Upsert this worker's heartbeat, keyed by {@code worker_id}, using the DB clock for both
     * {@code last_heartbeat} and {@code registered_at} so liveness comparisons stay on one clock.
     */
    @Modifying
    @Query(
        value = "INSERT INTO worker_registry (worker_id, last_heartbeat, registered_at) " +
            "VALUES (:workerId, now(), now()) " +
            "ON CONFLICT (worker_id) DO UPDATE SET last_heartbeat = now()",
        nativeQuery = true
    )
    void heartbeat(@Param("workerId") String workerId);

    /**
     * Delete registrations whose heartbeat is older than {@code ttlSeconds} (DB clock). Bounds table
     * growth from workers that died without a clean shutdown (SIGKILL) or from {@code worker_id} churn
     * (e.g. hostname-derived ids across pod restarts). Uses a TTL far longer than the orphan lease, so
     * any RUNNING job owned by such a worker has already been requeued before its row is removed.
     *
     * @return number of rows deleted
     */
    @Modifying
    @Query(
        value = "DELETE FROM worker_registry WHERE last_heartbeat < now() - make_interval(secs => :ttlSeconds)",
        nativeQuery = true
    )
    int deleteStale(@Param("ttlSeconds") long ttlSeconds);
}
