package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

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
     * The observations behind a batch of delivered feedback, carrying what decides their visibility.
     *
     * <p>Both revisions are fetch-joined because every caller compares them the moment it has a row:
     * an observation is only shown if the rules it was evaluated under are still the practice's current
     * ones. Fetched rather than joined — a plain join narrows the result and preloads nothing, so the
     * comparison would lazy-load its way through the batch one row at a time.
     */
    @Query(
        """
        SELECT fo.feedback.id AS feedbackId, observation AS observation
        FROM FeedbackObservation fo
        JOIN fo.observation observation
        JOIN FETCH observation.practice practice
        LEFT JOIN FETCH practice.currentRevision
        LEFT JOIN FETCH observation.practiceRevision
        WHERE fo.feedback.workspaceId = :workspaceId
          AND fo.feedback.id IN :feedbackIds
        """
    )
    List<FeedbackObservationVisibility> findForVisibility(
        @Param("workspaceId") Long workspaceId,
        @Param("feedbackIds") Collection<UUID> feedbackIds
    );

    interface FeedbackObservationVisibility {
        UUID getFeedbackId();
        Observation getObservation();
    }

    /**
     * Observation ids already bound to a SUPPRESSED unit of this job (reaction suppression writes its
     * {@code REACTED_*} units before the DELIVERED unit is recorded). The DELIVERED binding excludes
     * these so a withheld observation is never also counted as delivered.
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

    /** Private reflection uses the newest available body, including {@code FAILED}; other states are ineligible. */
    @Query(
        value = """
        SELECT DISTINCT ON (fo.observation_id)
               fo.observation_id AS observationId,
               f.body AS body
        FROM feedback_observation fo
        JOIN feedback f ON f.id = fo.feedback_id
        WHERE fo.observation_id IN (:observationIds)
          AND f.delivery_state IN ('DELIVERED', 'FAILED')
          AND f.body IS NOT NULL
        ORDER BY fo.observation_id, f.created_at DESC, f.id DESC
        """,
        nativeQuery = true
    )
    List<ObservationAdviceBody> findLatestAdviceBodiesByObservationIds(
        @Param("observationIds") Collection<UUID> observationIds
    );

    interface ObservationAdviceBody {
        UUID getObservationId();
        String getBody();
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
               fo.feedback.artifactKind AS artifactKind,
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
        Pageable pageable
    );

    @Query(
        """
        SELECT fo.observation.id AS observationId, fo.role AS role, fo.ordinal AS ordinal,
               p.slug AS practiceSlug, p.name AS practiceName, o.title AS title,
               pa.slug AS areaSlug, pa.name AS areaName, pa.icon AS areaIcon, pa.color AS areaColor,
               o.presence AS presence, o.assessment AS assessment, o.severity AS severity,
               o.confidence AS confidence, evaluatedRevision.id AS practiceRevisionId,
               evaluatedRevision.reviewRuleFingerprint AS practiceRevisionFingerprint,
               currentRevision.reviewRuleFingerprint AS currentPracticeRevisionFingerprint,
               o.observedAt AS observedAt
        FROM FeedbackObservation fo
        JOIN fo.observation o
        JOIN o.practice p
        LEFT JOIN o.practiceRevision evaluatedRevision
        LEFT JOIN p.currentRevision currentRevision
        LEFT JOIN p.area pa
        WHERE fo.feedback.id = :feedbackId
          AND fo.feedback.workspaceId = :workspaceId
          AND p.workspace.id = :workspaceId
        ORDER BY fo.ordinal ASC
        """
    )
    List<BoundObservation> findBoundObservations(
        @Param("workspaceId") Long workspaceId,
        @Param("feedbackId") UUID feedbackId
    );

    @Query(
        """
        SELECT fo.feedback.id AS feedbackId, fo.role AS role,
               fo.feedback.agentJobId AS agentJobId, fo.feedback.channel AS channel,
               fo.feedback.deliveryState AS deliveryState, fo.feedback.suppressionReason AS suppressionReason,
               fo.feedback.createdAt AS createdAt, fo.feedback.deliveredAt AS deliveredAt
        FROM FeedbackObservation fo
        WHERE fo.observation.id = :observationId AND fo.feedback.workspaceId = :workspaceId
        ORDER BY fo.feedback.createdAt DESC, fo.feedback.id DESC
        """
    )
    List<BoundFeedbackUnit> findBoundFeedbackUnits(
        @Param("workspaceId") Long workspaceId,
        @Param("observationId") UUID observationId
    );

    interface BoundObservation {
        UUID getObservationId();
        EvidenceRole getRole();
        Integer getOrdinal();
        String getPracticeSlug();
        String getPracticeName();
        String getAreaSlug();
        String getAreaName();
        String getAreaIcon();
        String getAreaColor();
        String getTitle();
        Presence getPresence();
        Assessment getAssessment();
        Severity getSeverity();
        Float getConfidence();
        Long getPracticeRevisionId();
        String getPracticeRevisionFingerprint();
        String getCurrentPracticeRevisionFingerprint();
        Instant getObservedAt();
    }

    interface BoundFeedbackUnit {
        UUID getFeedbackId();
        EvidenceRole getRole();
        UUID getAgentJobId();
        FeedbackChannel getChannel();
        FeedbackDeliveryState getDeliveryState();
        FeedbackSuppressionReason getSuppressionReason();
        Instant getCreatedAt();
        Instant getDeliveredAt();
    }

    /** Projection: facts + practice for one PREPARED conversational unit (no body - composed at delivery). */
    interface PreparedConversationFact {
        UUID getFeedbackId();
        UUID getObservationId();
        String getPracticeSlug();
        String getPracticeName();
        String getTitle();
        String getReasoning();
        Severity getSeverity();
        ArtifactKind getArtifactKind();
        Long getArtifactId();
        Instant getPreparedAt();
    }
}
