package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
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
    /** Idempotency guard for the ledger recorder: has this job already recorded this unit? */
    boolean existsByAgentJobIdAndPosition(UUID agentJobId, Integer position);

    /** Workspace-scoped lookup of a single feedback unit (reaction authorization + tenancy isolation). */
    Optional<Feedback> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    /**
     * The headline locus of a feedback unit: the {@code recurrence_key} of its earliest {@code PRIMARY}-role
     * bound observation. Denormalized onto a {@link de.tum.cit.aet.hephaestus.practices.observation.reaction.Reaction}
     * at write time so B2 suppression (ADR 0021) can follow a reacted locus across the detector's per-run
     * re-detections, even though the per-run feedback row differs each run. Null-key PRIMARY rows are
     * SKIPPED (the {@code recurrenceKey IS NOT NULL} filter): this returns the earliest PRIMARY observation
     * that HAS a non-null key. Empty only when the unit binds no PRIMARY observation with a recurrence_key.
     */
    @Query(
        """
        SELECT fo.observation.recurrenceKey FROM FeedbackObservation fo
        WHERE fo.feedback.id = :feedbackId
          AND fo.role = de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole.PRIMARY
          AND fo.observation.recurrenceKey IS NOT NULL
        ORDER BY fo.ordinal ASC
        LIMIT 1
        """
    )
    Optional<String> findHeadlineRecurrenceKey(@Param("feedbackId") UUID feedbackId);

    /**
     * The feedback a developer actually RECEIVED in a workspace — only units that reached a surface
     * ({@code DELIVERED}), newest first. Powers the mentor's delivered-feedback aspect so the coach
     * references the exact words the student saw ({@link Feedback#getBody()}) instead of
     * reconstructing from raw pre-delivery findings (which may have been suppressed, superseded, or never
     * postable). Bounded by the caller's {@code Pageable}.
     *
     * <p>Filters and orders on {@code created_at} (not {@code delivered_at}) so the
     * {@code idx_feedback_recipient_created (recipient_user_id, created_at DESC)} index serves both the
     * {@code >= :since} range and the {@code ORDER BY}. This is behaviour-equivalent: a DELIVERED unit is
     * written with {@code createdAt == deliveredAt} (same {@code Instant.now()} — see
     * {@code FeedbackLedgerRecorder}), so the two timestamps coincide for every row this query can match.
     */
    @Query(
        """
        SELECT f FROM Feedback f
        WHERE f.workspaceId = :workspaceId
          AND f.recipientUserId = :recipientUserId
          AND f.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.DELIVERED
          AND f.createdAt >= :since
        ORDER BY f.createdAt DESC
        """
    )
    List<Feedback> findRecentDeliveredForRecipient(
        @Param("workspaceId") Long workspaceId,
        @Param("recipientUserId") Long recipientUserId,
        @Param("since") Instant since,
        Pageable pageable
    );

    /**
     * The current (not-yet-superseded) delivery for a continuity line — the prior unit a re-review
     * supersedes and whose comment it edits in place. There is at most one live row per key by
     * construction (each new delivery flips the previous to {@code SUPERSEDED}).
     */
    Optional<Feedback> findFirstByThreadKeyAndDeliveryStateOrderByCreatedAtDesc(
        String threadKey,
        FeedbackDeliveryState state
    );

    /**
     * Flip a delivered unit to {@code SUPERSEDED} when a newer delivery for the same continuity line lands.
     * Native (not JPQL) because the {@code @Immutable} entity forbids ORM updates; the row's {@code state}
     * is the one lifecycle column and is transitioned only through this explicit statement.
     *
     * <p>{@code state} is written verbatim into the {@code delivery_state varchar(16)} column. It IS guarded
     * by the {@code chk_feedback_state} CHECK (PREPARED/DELIVERED/SUPERSEDED/SUPPRESSED/FAILED), so a typo
     * fails fast at write time; callers still MUST pass a {@link FeedbackDeliveryState#name()} value.
     *
     * <p><strong>Concurrency invariant:</strong> the supersession is a guarded, idempotent
     * transition — it flips a row to {@code :state} only while it is still {@code DELIVERED}. Two concurrent
     * re-reviews of the same artifact can both read the same prior DELIVERED row and both attempt to flip it;
     * the {@code AND delivery_state = 'DELIVERED'} predicate makes the second flip a no-op (affected rows = 0)
     * instead of double-superseding, and the returned count lets a caller detect the lost race. The "at most
     * one live (DELIVERED) row per continuity/thread key" invariant still relies on the orchestrator
     * serializing recorder runs for a given thread key for the {@code INSERT}; this guard hardens the flip.
     *
     * @return the number of rows updated — {@code 1} on a clean supersession, {@code 0} if the row was no
     *     longer {@code DELIVERED} (already superseded by a concurrent writer).
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE feedback SET delivery_state = :state WHERE id = :id AND delivery_state = 'DELIVERED'",
        nativeQuery = true
    )
    int updateState(@Param("id") UUID id, @Param("state") String state);

    /**
     * Purge all feedback for a workspace. The soft-delete that drives a workspace purge never fires the
     * RESTRICT FK on {@code feedback}, so feedback (and its CASCADE children {@code feedback_observation},
     * {@code feedback_placement}, {@code feedback_reaction}) would otherwise persist indefinitely. Called
     * first by the practices purge contributor.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Feedback f WHERE f.workspaceId = :workspaceId")
    void deleteAllByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
