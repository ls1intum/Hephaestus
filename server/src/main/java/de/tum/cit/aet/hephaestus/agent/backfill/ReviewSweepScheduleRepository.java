package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Standing sweep instructions. */
@Repository
@WorkspaceAgnostic("The scheduler acts on due schedules for every workspace on the instance")
public interface ReviewSweepScheduleRepository extends JpaRepository<ReviewSweepSchedule, UUID> {
    List<ReviewSweepSchedule> findByWorkspaceIdOrderByArtifactKindAsc(Long workspaceId);

    Optional<ReviewSweepSchedule> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    boolean existsByWorkspaceIdAndArtifactKind(Long workspaceId, String artifactKind);

    /**
     * Due, enabled schedules, longest-overdue first, with the workspace fetched.
     *
     * <p>The fetch is not an optimisation: the scheduler reads the workspace's status and feature flags
     * outside any transaction of its own, and a lazy proxy there is a {@code LazyInitializationException}
     * on the first tick after a restart rather than at review time.
     *
     * <p>Ordered by {@code nextRunAt} so a workspace whose sweep keeps being skipped cannot be starved
     * behind one that is merely newer.
     */
    @Query(
        """
        SELECT s FROM ReviewSweepSchedule s
        JOIN FETCH s.workspace
        WHERE s.enabled = TRUE
          AND s.nextRunAt <= :now
        ORDER BY s.nextRunAt ASC
        """
    )
    List<ReviewSweepSchedule> findDue(@Param("now") Instant now, Pageable pageable);

    /** Loads one schedule with its workspace, for the transaction that opens a run from it. */
    @Query(
        """
        SELECT s FROM ReviewSweepSchedule s
        JOIN FETCH s.workspace
        WHERE s.id = :id
        """
    )
    Optional<ReviewSweepSchedule> findByIdWithWorkspace(@Param("id") UUID id);
}
