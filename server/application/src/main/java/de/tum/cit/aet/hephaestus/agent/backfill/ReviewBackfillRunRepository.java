package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

/**
 * Campaign rows.
 *
 * <p>Deliberately no optimistic-claim dance around the driver's batch: two racing drivers would each try
 * to record the same signals, and {@code uq_artifact_signal} settles that in the database — the loser
 * submits nothing. The worst outcome of a race is a double-counted {@code passed_count}, a display
 * artefact, not a duplicated review or charge.
 */
@Repository
@WorkspaceAgnostic("The driver sweeps active campaigns for every workspace on the instance")
public interface ReviewBackfillRunRepository extends JpaRepository<ReviewBackfillRun, UUID> {
    List<ReviewBackfillRun> findByWorkspaceIdOrderByCreatedAtDesc(Long workspaceId, Pageable pageable);

    Optional<ReviewBackfillRun> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    /**
     * One at a time: two concurrent campaigns over overlapping scopes would each see the other's ledger
     * rows as "already covered", so neither would cover the scope the admin's confirmed estimate was for.
     */
    boolean existsByWorkspaceIdAndStatusIn(Long workspaceId, Collection<ReviewBackfillStatus> statuses);

    /**
     * Active campaigns least-recently-touched first, so one long run cannot starve a newer one and a
     * restart resumes in roughly the order it stopped.
     */
    @Query(
        """
        SELECT r FROM ReviewBackfillRun r
        JOIN FETCH r.workspace
        WHERE r.status IN :statuses
        ORDER BY r.updatedAt ASC
        """
    )
    List<ReviewBackfillRun> findByStatusIn(Collection<ReviewBackfillStatus> statuses, Pageable pageable);
}
