package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for the immutable {@link FeedbackObservation} M:N join binding a {@link Feedback} unit to the
 * {@link de.tum.cit.aet.hephaestus.practices.model.Observation}s it was composed from.
 *
 * <p>Written via a native {@code ON CONFLICT DO NOTHING} upsert (mirrors {@code Observation
 * .insertIfAbsent}) — the {@code @EmbeddedId}/{@code @MapsId} entity is awkward to build for a plain
 * {@code save()}, and the native path is race-safe on a recorder retry.
 *
 * <p>Workspace-agnostic: the join row carries no tenant column — it is scoped through its parent
 * {@link Feedback} (which holds {@code workspace_id}), so callers tenant-scope at the {@code Feedback} level.
 */
@Repository
@WorkspaceAgnostic("FeedbackObservation is a join row scoped through its parent Feedback's workspace_id, not its own")
public interface FeedbackObservationRepository extends JpaRepository<FeedbackObservation, FeedbackObservation.Id> {
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO feedback_observation (feedback_id, observation_id, role, ordinal)
        VALUES (:feedbackId, :observationId, :evidenceRole, :ordinal)
        ON CONFLICT (feedback_id, observation_id) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("feedbackId") UUID feedbackId,
        @Param("observationId") UUID observationId,
        @Param("evidenceRole") String evidenceRole,
        @Param("ordinal") int ordinal
    );

    /**
     * Observation ids already bound to a SUPPRESSED unit of this job — i.e. withheld earlier in the flow (B2
     * reaction suppression writes its {@code REACTED_*} units before the DELIVERED unit is recorded). The
     * DELIVERED binding excludes these so a withheld observation is never also counted as delivered.
     */
    @Query(
        value = """
        SELECT ff.observation_id FROM feedback_observation ff
        JOIN feedback f ON f.id = ff.feedback_id
        WHERE f.agent_job_id = :agentJobId AND f.delivery_state = 'SUPPRESSED'
        """,
        nativeQuery = true
    )
    List<UUID> findObservationIdsSuppressedForJob(@Param("agentJobId") UUID agentJobId);

    /**
     * The DELIVERED feedback body bound to each of the given observations — the developer's advice source for
     * the read surfaces (reflection dashboard, observation detail). Per ADR 0021 the immutable {@code Observation}
     * carries evidence + observation + reasoning but NO advice; advice is composed into the delivered {@code Feedback}
     * and read back from the delivered {@code Feedback}'s {@code body} column here.
     *
     * <p>An observation can be bound to more than one DELIVERED unit (e.g. successive re-deliveries), so this can
     * return multiple rows per observation id; callers keep the most recent by {@code feedbackCreatedAt}. Only
     * {@code DELIVERED} units with a non-null body are returned (PREPARED/SUPPRESSED/FAILED carry no body the
     * developer ever saw).
     */
    @Query(
        """
        SELECT ff.observation.id AS observationId,
               ff.feedback.body AS body,
               ff.feedback.createdAt AS feedbackCreatedAt
        FROM FeedbackObservation ff
        WHERE ff.observation.id IN :observationIds
          AND ff.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.DELIVERED
          AND ff.feedback.body IS NOT NULL
        """
    )
    List<DeliveredObservationBody> findDeliveredBodiesByObservationIds(
        @Param("observationIds") Collection<UUID> observationIds
    );

    /** Projection: an observation id paired with a DELIVERED feedback body and that feedback's creation time. */
    interface DeliveredObservationBody {
        UUID getObservationId();
        String getBody();
        Instant getFeedbackCreatedAt();
    }

    // --- conversational feedback delivery loop ---

    /**
     * The id(s) of the PREPARED CONVERSATION feedback unit(s) for this recipient/workspace bound (as PRIMARY) to the
     * given observation. Maps a mentor {@code link_finding} observation id back to the unit to flip to DELIVERED.
     * Ordered newest-first so a caller can take the first; the reconciler's CAS makes any duplicate flip a no-op.
     */
    @Query(
        """
        SELECT fo.feedback.id
        FROM FeedbackObservation fo
        WHERE fo.observation.id = :observationId
          AND fo.role = de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole.PRIMARY
          AND fo.feedback.workspaceId = :workspaceId
          AND fo.feedback.recipientUserId = :recipientUserId
          AND fo.feedback.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.CONVERSATION
          AND fo.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.PREPARED
        ORDER BY fo.feedback.createdAt DESC
        """
    )
    List<UUID> findPreparedConversationFeedbackIdsByObservation(
        @Param("workspaceId") Long workspaceId,
        @Param("recipientUserId") Long recipientUserId,
        @Param("observationId") UUID observationId
    );

    /**
     * Facts + practice (NO body) for the newest PREPARED CONVERSATION units of a recipient - the payload the
     * {@code PreparedConversationFeedbackContentSource} ships to the mentor. Body is deliberately absent (the mentor
     * composes the words at delivery). Ordered newest-first, bounded by the caller's {@code Pageable}.
     */
    @Query(
        """
        SELECT fo.feedback.id AS feedbackId,
               o.id AS observationId,
               p.slug AS practiceSlug,
               p.name AS practiceName,
               o.title AS title,
               o.reasoning AS reasoning,
               o.severity AS severity,
               fo.feedback.artifactType AS artifactType,
               fo.feedback.artifactId AS artifactId,
               fo.feedback.createdAt AS preparedAt
        FROM FeedbackObservation fo
        JOIN fo.observation o
        JOIN o.practice p
        WHERE fo.role = de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole.PRIMARY
          AND fo.feedback.workspaceId = :workspaceId
          AND fo.feedback.recipientUserId = :recipientUserId
          AND fo.feedback.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.CONVERSATION
          AND fo.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.PREPARED
        ORDER BY fo.feedback.createdAt DESC
        """
    )
    List<PreparedConversationFact> findPreparedConversationFactsForRecipient(
        @Param("workspaceId") Long workspaceId,
        @Param("recipientUserId") Long recipientUserId,
        org.springframework.data.domain.Pageable pageable
    );

    /** Projection: facts + practice for one PREPARED conversational unit (no body - composed at delivery). */
    interface PreparedConversationFact {
        UUID getFeedbackId();
        UUID getObservationId();
        String getPracticeSlug();
        String getPracticeName();
        String getTitle();
        String getReasoning();
        de.tum.cit.aet.hephaestus.practices.model.Severity getSeverity();
        de.tum.cit.aet.hephaestus.practices.model.WorkArtifact getArtifactType();
        Long getArtifactId();
        Instant getPreparedAt();
    }
}
