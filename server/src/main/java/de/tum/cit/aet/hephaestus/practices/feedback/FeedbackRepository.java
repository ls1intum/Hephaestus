package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@WorkspaceAgnostic("Feedback is scoped by a raw workspace_id scalar (cross-module FK), not a Workspace association")
public interface FeedbackRepository extends JpaRepository<Feedback, UUID> {
    /** Idempotency guard for the ledger recorder: has this job already recorded this unit? */
    boolean existsByAgentJobIdAndPosition(UUID agentJobId, Integer position);

    /** Workspace-scoped lookup of a single feedback unit (reaction authorization + tenancy isolation). */
    Optional<Feedback> findByIdAndWorkspaceId(UUID id, Long workspaceId);

    @Query(
        value = """
        SELECT f.agent_job_id AS "jobId",
               COUNT(*) FILTER (WHERE f.delivery_state = 'PREPARED') AS "prepared",
               COUNT(*) FILTER (WHERE f.delivery_state = 'DELIVERED') AS "delivered",
               COUNT(*) FILTER (WHERE f.delivery_state = 'SUPERSEDED') AS "superseded",
               COUNT(*) FILTER (WHERE f.delivery_state = 'SUPPRESSED') AS "suppressed",
               COUNT(*) FILTER (WHERE f.delivery_state = 'FAILED') AS "failed"
        FROM feedback f
        WHERE f.workspace_id = :workspaceId
          AND f.agent_job_id IN :jobIds
        GROUP BY f.agent_job_id
        """,
        nativeQuery = true
    )
    List<ReviewFeedbackCounts> summarizeReviewFeedback(
        @Param("workspaceId") Long workspaceId,
        @Param("jobIds") Collection<UUID> jobIds
    );

    interface ReviewFeedbackCounts {
        UUID getJobId();
        Long getPrepared();
        Long getDelivered();
        Long getSuperseded();
        Long getSuppressed();
        Long getFailed();
    }

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

    /** Delivered summary and inline-only feedback for a recipient, newest first. */
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
     * Supersedes the prior delivered summary selected through its SUMMARY placement. Inline-only deliveries
     * intentionally remain DELIVERED on the same thread. The state predicate makes concurrent retries idempotent.
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

    /**
     * Hard-delete the {@code CONVERSATION_THREAD} feedback for a workspace whose {@code artifact_id} (the
     * {@code slack_thread} id) is one of {@code artifactIds} — the derived-content erasure the Slack module invokes
     * through {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure} when a channel's consent is
     * withdrawn. DB {@code ON DELETE CASCADE} clears {@code feedback_observation} / {@code feedback_placement} /
     * {@code feedback_reaction}. Bulk JPQL delete (the {@code @Immutable} entity forbids an ORM remove). The
     * {@code workspace_id} + {@code artifact_type} + {@code artifact_id} predicates keep it scoped so no PR/ISSUE unit
     * and no other-tenant row is affected. Callers guard an empty {@code artifactIds}.
     *
     * @return the number of feedback units deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Feedback f
        WHERE f.workspaceId = :workspaceId
          AND f.artifactType = de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.CONVERSATION_THREAD
          AND f.artifactId IN :artifactIds
        """
    )
    int deleteConversationThreadFeedback(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactIds") Collection<Long> artifactIds
    );

    /**
     * Hard-delete <em>every</em> {@code CONVERSATION_THREAD} feedback unit for a workspace — the whole-tenant erasure
     * the Slack module invokes through
     * {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure#eraseAllConversationForWorkspace} on
     * app-uninstall / workspace-purge. DB {@code ON DELETE CASCADE} clears {@code feedback_observation} /
     * {@code feedback_placement} / {@code feedback_reaction}. The {@code workspace_id} + {@code artifact_type}
     * predicates keep it scoped so no PR/ISSUE unit and no other-tenant row is affected. Idempotent (0 when the
     * workspace has no conversation feedback).
     *
     * @return the number of feedback units deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Feedback f
        WHERE f.workspaceId = :workspaceId
          AND f.artifactType = de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.CONVERSATION_THREAD
        """
    )
    int deleteAllConversationThreadFeedback(@Param("workspaceId") Long workspaceId);

    /**
     * Hard-delete every {@code PULL_REQUEST} / {@code ISSUE} feedback unit for a workspace — the
     * SCM-derived counterpart of {@link #deleteAllConversationThreadFeedback}, invoked when the
     * workspace's SCM mirror is erased on connection-disconnect or workspace-purge. These units hold
     * mirrored third-party content directly (quoted diff/comment text in the evidence payload) and
     * reference the artifact only by a soft {@code artifact_id}, so they neither cascade with the
     * repository delete nor survive it meaningfully. DB {@code ON DELETE CASCADE} clears
     * {@code feedback_observation} / {@code feedback_placement} / {@code feedback_reaction}. The
     * {@code workspace_id} + {@code artifact_type} predicates keep {@code CONVERSATION_THREAD} units
     * and other tenants' rows untouched. Idempotent.
     *
     * @return the number of feedback units deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Feedback f
        WHERE f.workspaceId = :workspaceId
          AND f.artifactType IN (
            de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.PULL_REQUEST,
            de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.ISSUE
          )
        """
    )
    int deleteAllScmArtifactFeedback(@Param("workspaceId") Long workspaceId);

    /**
     * Hard-delete the {@code CONVERSATION_THREAD} feedback a single person is the <em>subject</em> of
     * ({@code about_user_id = :aboutUserId}) within a workspace — the derived-content half of a person opt-out /
     * account hard-delete, invoked through
     * {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure#eraseConversationFeedbackAboutUser}.
     * DB {@code ON DELETE CASCADE} clears the join/placement/reaction children. The {@code workspace_id} +
     * {@code artifact_type} + {@code about_user_id} predicates keep another person's rows, PR/ISSUE rows, and other
     * tenants' rows intact. Idempotent.
     *
     * @return the number of feedback units deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Feedback f
        WHERE f.workspaceId = :workspaceId
          AND f.artifactType = de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.CONVERSATION_THREAD
          AND f.aboutUserId = :aboutUserId
        """
    )
    int deleteConversationThreadFeedbackAboutUser(
        @Param("workspaceId") Long workspaceId,
        @Param("aboutUserId") Long aboutUserId
    );

    // --- conversational feedback delivery loop ---

    /**
     * Flip a PREPARED conversational unit to DELIVERED (compare-and-set). Native (not JPQL) because the
     * {@code @Immutable} entity forbids ORM state mutation, mirroring {@link #updateState}. The
     * {@code delivery_state='PREPARED'} predicate is the CAS guard: exactly one of N racing mentor turns wins the
     * flip; the others see a rowcount of 0. A unit already DELIVERED, SUPPRESSED (aged out), or non-existent yields
     * 0 - the caller treats that as a no-op and does NOT write a placement.
     *
     * @return {@code 1} on a clean flip, {@code 0} if the unit was no longer PREPARED (lost race / expired).
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE feedback SET delivery_state = 'DELIVERED', delivered_at = :at " +
            "WHERE id = :id AND delivery_state = 'PREPARED'",
        nativeQuery = true
    )
    int markConversationDelivered(@Param("id") UUID id, @Param("at") Instant at);

    /**
     * Flip a PREPARED conversational unit to SUPPRESSED when its actual transport attempt was blocked by
     * instance Silent Mode. The state predicate prevents overwriting a unit another transaction already delivered.
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE feedback SET delivery_state = 'SUPPRESSED', suppression_reason = 'INSTANCE_SILENCED' " +
            "WHERE id = :id AND delivery_state = 'PREPARED'",
        nativeQuery = true
    )
    int markConversationSuppressedBySilentMode(@Param("id") UUID id);

    /**
     * Newest PREPARED conversational units for a developer (as RECIPIENT) in a workspace - the mentor's queue.
     * Body is intentionally NULL on these rows (composed at delivery). Ordered newest-first, bounded by the caller's
     * {@code Pageable}.
     */
    @Query(
        """
        SELECT f FROM Feedback f
        WHERE f.workspaceId = :workspaceId
          AND f.recipientUserId = :recipientUserId
          AND f.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.CONVERSATION
          AND f.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.PREPARED
        ORDER BY f.createdAt DESC
        """
    )
    List<Feedback> findRecentPreparedConversationForRecipient(
        @Param("workspaceId") Long workspaceId,
        @Param("recipientUserId") Long recipientUserId,
        Pageable pageable
    );

    /**
     * Does a DELIVERED IN_CONTEXT feedback unit already exist for this recipient in this workspace bound to an
     * observation carrying {@code recurrenceKey}? The router uses this to avoid re-raising a locus already received
     * inline. A null key is never passed (the caller skips the check when the key is null).
     */
    @Query(
        """
        SELECT (COUNT(f) > 0) FROM Feedback f, FeedbackObservation fo
        WHERE fo.feedback = f
          AND fo.observation.recurrenceKey = :recurrenceKey
          AND f.workspaceId = :workspaceId
          AND f.recipientUserId = :recipientUserId
          AND f.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.IN_CONTEXT
          AND f.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.DELIVERED
        """
    )
    boolean existsDeliveredInContextForRecurrenceKey(
        @Param("workspaceId") Long workspaceId,
        @Param("recipientUserId") Long recipientUserId,
        @Param("recurrenceKey") String recurrenceKey
    );

    /** Distinct workspaces that currently hold at least one PREPARED conversational unit (TTL sweep enumeration). */
    @Query(
        """
        SELECT DISTINCT f.workspaceId FROM Feedback f
        WHERE f.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.CONVERSATION
          AND f.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.PREPARED
        """
    )
    List<Long> findWorkspaceIdsWithPreparedConversation();

    /**
     * Age out every PREPARED conversational unit for a workspace created strictly before {@code cutoff}: SUPPRESSED /
     * CONVERSATION_EXPIRED. Native - the {@code @Immutable} entity forbids an ORM update. Carries the
     * {@code workspace_id} predicate the tenancy inspector requires for a raw native statement.
     *
     * @return the number of units expired
     */
    @Modifying
    @Transactional
    @Query(
        value = "UPDATE feedback SET delivery_state = 'SUPPRESSED', suppression_reason = 'CONVERSATION_EXPIRED' " +
            "WHERE workspace_id = :workspaceId AND channel = 'CONVERSATION' " +
            "AND delivery_state = 'PREPARED' AND created_at < :cutoff",
        nativeQuery = true
    )
    int expirePreparedConversationBefore(@Param("workspaceId") Long workspaceId, @Param("cutoff") Instant cutoff);

    int BODY_PREVIEW_LENGTH = 320;

    String OPERATOR_PREDICATES = """
          AND (CAST(:#{#f.deliveryStateNames()} AS text[]) IS NULL OR f.delivery_state = ANY(CAST(:#{#f.deliveryStateNames()} AS text[])))
          AND (CAST(:#{#f.suppressionReasonNames()} AS text[]) IS NULL OR f.suppression_reason = ANY(CAST(:#{#f.suppressionReasonNames()} AS text[])))
          AND (CAST(:#{#f.channelNames()} AS text[]) IS NULL OR f.channel = ANY(CAST(:#{#f.channelNames()} AS text[])))
          AND (CAST(:#{#f.agentJobId()} AS uuid) IS NULL OR f.agent_job_id = CAST(:#{#f.agentJobId()} AS uuid))
          AND (CAST(:#{#f.artifactTypeName()} AS text) IS NULL OR f.artifact_type = CAST(:#{#f.artifactTypeName()} AS text))
          AND (CAST(:#{#f.artifactId()} AS bigint) IS NULL OR f.artifact_id = CAST(:#{#f.artifactId()} AS bigint))
          AND (CAST(:#{#f.recipientUserId()} AS bigint) IS NULL OR f.recipient_user_id = CAST(:#{#f.recipientUserId()} AS bigint))
          AND (CAST(:#{#f.from()} AS timestamptz) IS NULL OR f.created_at >= CAST(:#{#f.from()} AS timestamptz))
          AND (CAST(:#{#f.to()} AS timestamptz) IS NULL OR f.created_at < CAST(:#{#f.to()} AS timestamptz))
        """;

    @Query(
        value = "SELECT f.id AS \"id\"," +
            " f.agent_job_id AS \"agentJobId\"," +
            " f.artifact_type AS \"artifactType\"," +
            " f.artifact_id AS \"artifactId\"," +
            " f.recipient_user_id AS \"recipientUserId\"," +
            " f.about_user_id AS \"aboutUserId\"," +
            " f.channel AS \"channel\"," +
            " f.delivery_state AS \"deliveryState\"," +
            " f.suppression_reason AS \"suppressionReason\"," +
            " f.replaces_id AS \"replacesId\"," +
            " f.created_at AS \"createdAt\"," +
            " f.delivered_at AS \"deliveredAt\"," +
            " left(f.body, " +
            BODY_PREVIEW_LENGTH +
            ") AS \"bodyPreview\"," +
            " (f.body IS NOT NULL AND length(f.body) > " +
            BODY_PREVIEW_LENGTH +
            ") AS \"bodyTruncated\"," +
            " (SELECT count(*) FROM feedback_observation fo" +
            " JOIN observation o ON o.id = fo.observation_id" +
            " JOIN practice p ON p.id = o.practice_id" +
            " WHERE fo.feedback_id = f.id AND p.workspace_id = f.workspace_id) AS \"observationCount\"" +
            " FROM feedback f WHERE f.workspace_id = :workspaceId" +
            OPERATOR_PREDICATES +
            " ORDER BY f.created_at DESC, f.id DESC",
        countQuery = "SELECT count(*) FROM feedback f WHERE f.workspace_id = :workspaceId" + OPERATOR_PREDICATES,
        nativeQuery = true
    )
    Page<OperatorFeedbackRow> findForWorkspace(
        @Param("workspaceId") Long workspaceId,
        @Param("f") FeedbackQueryFilter filter,
        Pageable pageable
    );

    interface OperatorFeedbackRow {
        UUID getId();
        UUID getAgentJobId();
        WorkArtifact getArtifactType();
        Long getArtifactId();
        Long getRecipientUserId();
        Long getAboutUserId();
        FeedbackChannel getChannel();
        FeedbackDeliveryState getDeliveryState();
        FeedbackSuppressionReason getSuppressionReason();

        UUID getReplacesId();

        Instant getCreatedAt();
        Instant getDeliveredAt();

        String getBodyPreview();

        Boolean getBodyTruncated();

        Long getObservationCount();
    }
}
