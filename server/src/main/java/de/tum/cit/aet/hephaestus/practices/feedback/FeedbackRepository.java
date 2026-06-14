package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for immutable {@link Feedback} units with append-only semantics.
 *
 * <p>Workspace-agnostic: {@code Feedback} carries a raw {@code workspace_id} scalar rather than a
 * {@code @ManyToOne} workspace association (cross-module FK), so the standard tenancy filter does not
 * apply here — callers scope by {@code workspaceId} explicitly.
 */
@Repository
@WorkspaceAgnostic("Feedback is scoped by a raw workspace_id scalar (cross-module FK), not a Workspace association")
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    /**
     * All feedback units produced by a given agent job, in insertion order via {@code unit_ordinal}.
     */
    List<Feedback> findByAgentJobIdOrderByUnitOrdinalAsc(UUID agentJobId);

    /** Idempotency guard for the ledger recorder: has this job already recorded this unit? */
    boolean existsByAgentJobIdAndUnitOrdinal(UUID agentJobId, Integer unitOrdinal);

    /**
     * The current (not-yet-superseded) delivery for a continuity line — the prior unit a re-review
     * supersedes and whose comment it edits in place. There is at most one live row per key by
     * construction (each new delivery flips the previous to {@code SUPERSEDED}).
     */
    Optional<Feedback> findFirstByContinuityKeyAndStateOrderByCreatedAtDesc(String continuityKey, FeedbackState state);

    /**
     * Flip a delivered unit to {@code SUPERSEDED} when a newer delivery for the same continuity line lands.
     * Native (not JPQL) because the {@code @Immutable} entity forbids ORM updates; the row's {@code state}
     * is the one lifecycle column and is transitioned only through this explicit statement.
     */
    @Modifying
    @Transactional
    @Query(value = "UPDATE feedback SET state = :state WHERE id = :id", nativeQuery = true)
    int updateState(@Param("id") UUID id, @Param("state") String state);
}
