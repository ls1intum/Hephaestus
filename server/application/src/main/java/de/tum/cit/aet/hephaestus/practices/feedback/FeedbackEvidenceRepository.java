package de.tum.cit.aet.hephaestus.practices.feedback;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.ReviewClaimCurrentness;
import de.tum.cit.aet.hephaestus.practices.model.Outcome;
import de.tum.cit.aet.hephaestus.practices.model.PracticeResult;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.result.reaction.ReactionRepository.CurrentResponseProjection;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
@WorkspaceAgnostic("FeedbackEvidence is a join row scoped through its parent Feedback's workspace_id, not its own")
public interface FeedbackEvidenceRepository extends JpaRepository<FeedbackEvidence, UUID> {
    /**
     * Binds one piece of feedback to one result, or to one problem of it. The workspace is the feedback's own, and the
     * schema refuses a result or a problem of another workspace, or a problem of another result. A repeated binding is
     * a no-op.
     */
    @Modifying
    @Transactional
    @Query(value = """
        INSERT INTO feedback_evidence (id, workspace_id, feedback_id, practice_result_id, observation_id, role, ordinal)
        SELECT gen_random_uuid(), f.workspace_id, f.id, :practiceResultId, CAST(:observationId AS uuid),
               :evidenceRole, :ordinal
        FROM feedback f WHERE f.id = :feedbackId
          AND NOT EXISTS (SELECT 1 FROM feedback_evidence e
                          WHERE e.feedback_id = :feedbackId AND e.practice_result_id = :practiceResultId
                            AND e.observation_id IS NOT DISTINCT FROM CAST(:observationId AS uuid))
        ON CONFLICT DO NOTHING
        """, nativeQuery = true)
    int insertIfAbsent(
            @Param("feedbackId") UUID feedbackId,
            @Param("practiceResultId") UUID practiceResultId,
            @Param("observationId") @Nullable UUID observationId,
            @Param("evidenceRole") String evidenceRole,
            @Param("ordinal") int ordinal);

    @Query("""
        SELECT COUNT(fo) FROM FeedbackEvidence fo
        WHERE fo.feedback.id = :feedbackId AND fo.feedback.workspaceId = :workspaceId
        """)
    int countForFeedback(@Param("workspaceId") Long workspaceId, @Param("feedbackId") UUID feedbackId);

    /**
     * The results behind a batch of delivered feedback, carrying what decides their visibility.
     *
     * <p>Both revisions are fetch-joined because every caller compares them the moment it has a row, through
     * {@link de.tum.cit.aet.hephaestus.practices.result.ResultVisibilityPolicy}, which decides what the
     * comparison means for that caller. Fetched rather than joined — a plain join narrows the result and preloads
     * nothing, so the comparison would lazy-load its way through the batch one row at a time.
     */
    @Query("""
        SELECT fo.feedback.id AS feedbackId, practiceResult AS practiceResult
        FROM FeedbackEvidence fo
        JOIN fo.practiceResult practiceResult
        JOIN FETCH practiceResult.practice practice
        LEFT JOIN FETCH practice.currentRevision
        LEFT JOIN FETCH practiceResult.practiceRevision
        WHERE fo.feedback.workspaceId = :workspaceId
          AND fo.feedback.id IN :feedbackIds
        """)
    List<FeedbackResultVisibility> findForVisibility(
            @Param("workspaceId") Long workspaceId, @Param("feedbackIds") Collection<UUID> feedbackIds);

    interface FeedbackResultVisibility {
        UUID getFeedbackId();

        PracticeResult getPracticeResult();

        /**
         * The feedback rows every bound result of which is {@code visible}, each with the currentness of that
         * evidence: {@code CURRENT} only when every bound result still is. A row bound to nothing is absent.
         */
        static Map<UUID, ReviewClaimCurrentness> shown(
                Collection<FeedbackResultVisibility> bindings, Set<UUID> visible) {
            Map<UUID, Boolean> permitted = new HashMap<>();
            Map<UUID, ReviewClaimCurrentness> currentness = new HashMap<>();
            for (FeedbackResultVisibility binding : bindings) {
                PracticeResult practiceResult = binding.getPracticeResult();
                permitted.merge(binding.getFeedbackId(), visible.contains(practiceResult.getId()), Boolean::logicalAnd);
                boolean current = ReviewClaimCurrentness.of(
                                practiceResult.getPracticeRevision(),
                                practiceResult.getPractice(),
                                practiceResult.getSupersededAt())
                        == ReviewClaimCurrentness.CURRENT;
                currentness.merge(
                        binding.getFeedbackId(),
                        current ? ReviewClaimCurrentness.CURRENT : ReviewClaimCurrentness.STALE,
                        (a, b) -> a == ReviewClaimCurrentness.CURRENT ? b : a);
            }
            currentness.keySet().removeIf(feedbackId -> !Boolean.TRUE.equals(permitted.get(feedbackId)));
            return currentness;
        }
    }

    /**
     * Result ids already bound to a SUPPRESSED unit of this job (reaction suppression writes its
     * {@code REACTED_*} units before the DELIVERED unit is recorded). The DELIVERED binding excludes
     * these so a withheld result is never also counted as delivered.
     */
    @Query(value = """
        SELECT ff.practice_result_id FROM feedback_evidence ff
        JOIN feedback f ON f.id = ff.feedback_id
        WHERE f.agent_job_id = :agentJobId AND f.delivery_state = 'SUPPRESSED'
        """, nativeQuery = true)
    List<UUID> findResultIdsSuppressedForJob(@Param("agentJobId") UUID agentJobId);

    /**
     * The newest body said to this result's subject <em>about this result</em>, per result,
     * on the lanes the caller names. {@code FAILED} counts: the words were composed and the developer may
     * have seen them on the artifact; only states where nothing was ever said are ineligible.
     *
     * <p><b>{@code channels} is required, and deliberately has no default.</b> A {@link Feedback} body is
     * "what we told them", but three lanes can tell them something and they are not interchangeable: an
     * IN_CONTEXT note and a mentor turn are about the one result they are bound to, while an IN_APP
     * unit is a cross-artifact message that binds every problem behind it as PRIMARY evidence and is
     * about none of them individually. With no channel predicate this query's newest-first tie-break
     * hands a per-practiceResult surface the process message instead — so the lane a caller means is a fact only
     * the caller holds, and it has to state it.
     *
     * <p>Bound as names rather than as {@link FeedbackChannel} values because this is a native query, where
     * an enum parameter's JDBC mapping is not the string the column stores.
     *
     * <p><b>Scoped by {@code workspace_id}, like every other read here.</b> A result id is a UUID
     * and looks unguessable, but this join row is written by native SQL with no tenancy check of its own,
     * so the only thing standing between one tenant's private advice and another tenant's detail page is
     * this predicate. Without it the newest-first tie-break decides which tenant's words a reader gets.
     */
    @Query(value = """
        SELECT DISTINCT ON (fo.practice_result_id)
               fo.practice_result_id AS practiceResultId,
               f.body AS body
        FROM feedback_evidence fo
        JOIN feedback f ON f.id = fo.feedback_id
        WHERE fo.practice_result_id IN (:practiceResultIds)
          AND f.workspace_id = :workspaceId
          AND f.channel IN (:channels)
          AND f.delivery_state IN ('DELIVERED', 'FAILED')
          AND f.body IS NOT NULL
        ORDER BY fo.practice_result_id, f.created_at DESC, f.id DESC
        """, nativeQuery = true)
    List<ResultFeedbackBody> findLatestFeedbackBodiesByResultIds(
            @Param("workspaceId") Long workspaceId,
            @Param("practiceResultIds") Collection<UUID> practiceResultIds,
            @Param("channels") Collection<String> channels);

    interface ResultFeedbackBody {
        UUID getPracticeResultId();

        String getBody();
    }

    /**
     * The one piece of feedback a result's detail page is about, per observation: the newest one
     * addressed to this developer on the lanes the caller names that actually said something —
     * {@code DELIVERED}, or {@code FAILED} with a body — carrying its text, its id and the developer's standing
     * answer to it.
     *
     * <p><b>{@code DELIVERED} feedback qualifies without a body.</b> A package that landed only as line notes
     * records no summary text, yet the developer can read those notes and answer them; requiring a body would
     * hide the handle. Its text is then null. A {@code FAILED} row with no body said nothing anyone saw, so it
     * never shadows an older delivered one.
     *
     * <p><b>Text and handle come from one row</b>, so on a result carried by more than one piece of
     * feedback the developer always rates the words they just read. The channel set is the caller's to state
     * for the same reason {@link #findLatestFeedbackBodiesByResultIds} makes it state one: IN_APP feedback
     * is about a way of working across several pieces of work, not about the one result it is bound to.
     *
     * <p><b>{@code feedbackId} is null on {@code FAILED} feedback.</b> The words were composed and the
     * developer may have seen them on the artifact, so the text is shown; but the response endpoint accepts
     * only {@code DELIVERED} feedback, so there is no handle to answer it with.
     *
     * <p>Ordered by creation rather than by delivery time because FAILED feedback has no
     * {@code delivered_at}: newest composed wins, and ties break on id.
     *
     * <p>Bound as channel names rather than as {@link FeedbackChannel} values, and scoped by
     * {@code workspace_id}, for the reasons {@link #findLatestFeedbackBodiesByResultIds} spells out.
     */
    @Query(value = """
                SELECT DISTINCT ON (fo.practice_result_id)
                       fo.practice_result_id AS "practiceResultId",
                       CASE WHEN f.delivery_state = 'DELIVERED' THEN f.id END AS "feedbackId",
                       f.body AS "body",
                       response.usefulness AS "usefulness",
                       response.action AS "resolution",
                       response.explanation AS "comment",
                       response.created_at AS "respondedAt"
                FROM feedback_evidence fo
                JOIN feedback f ON f.id = fo.feedback_id
                LEFT JOIN LATERAL (
                    SELECT r.usefulness, r.action, r.explanation, r.created_at
                    FROM reaction r
                    WHERE r.feedback_id = f.id AND r.reactor_user_id = :recipientUserId
                    ORDER BY r.created_at DESC, r.id DESC LIMIT 1
        ) response ON TRUE
        WHERE fo.practice_result_id IN (:practiceResultIds)
          AND f.workspace_id = :workspaceId
          AND f.recipient_user_id = :recipientUserId
          AND f.channel IN (:channels)
          AND (f.delivery_state = 'DELIVERED' OR (f.delivery_state = 'FAILED' AND f.body IS NOT NULL))
        ORDER BY fo.practice_result_id, f.created_at DESC, f.id DESC
        """, nativeQuery = true)
    List<ResultFeedback> findLatestFeedbackByResultIds(
            @Param("workspaceId") Long workspaceId,
            @Param("recipientUserId") Long recipientUserId,
            @Param("practiceResultIds") Collection<UUID> practiceResultIds,
            @Param("channels") Collection<String> channels);

    /**
     * The piece of feedback a result's detail is about: what it said, the handle to answer it with when
     * it can be answered, and the developer's standing answer. The answer is the same shape the response endpoint
     * returns, so the result detail reads it through {@code FeedbackResponseDTO.from} rather than
     * flattening the columns a second time.
     */
    interface ResultFeedback extends CurrentResponseProjection {
        UUID getPracticeResultId();

        /** Null when the feedback is {@code FAILED}: its words are readable but it cannot be answered. */
        @Nullable
        UUID getFeedbackId();

        /** Null when delivered feedback recorded no text, as a package that landed only as line notes does. */
        @Nullable
        String getBody();
    }

    /**
     * The id(s) of the PREPARED IN_CHAT piece of feedback(s) for this recipient/workspace bound (as PRIMARY) to the
     * given result. Maps a mentor {@code link_observation} id back to the unit to flip to DELIVERED.
     * Ordered newest-first so a caller can take the first; the reconciler's CAS makes any duplicate flip a no-op.
     */
    @Query("""
        SELECT fo.feedback.id
        FROM FeedbackEvidence fo
        WHERE fo.practiceResult.id = :practiceResultId
          AND fo.role = de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole.PRIMARY
          AND fo.feedback.workspaceId = :workspaceId
          AND fo.feedback.recipientUserId = :recipientUserId
          AND fo.feedback.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.IN_CHAT
          AND fo.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.PREPARED
        ORDER BY fo.feedback.createdAt DESC
        """)
    List<UUID> findPreparedConversationFeedbackIdsByResult(
            @Param("workspaceId") Long workspaceId,
            @Param("recipientUserId") Long recipientUserId,
            @Param("practiceResultId") UUID practiceResultId);

    @Query("""
        SELECT DISTINCT fo.feedback.id FROM FeedbackEvidence fo JOIN fo.practiceResult o
        WHERE fo.feedback.workspaceId = :workspaceId
          AND fo.feedback.recipientUserId = :recipientUserId
          AND fo.feedback.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.IN_CHAT
          AND fo.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.PREPARED
          AND fo.role = de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole.PRIMARY
          AND o.practice.id = :practiceId AND o.artifactKind = :artifactKind AND o.artifactId = :artifactId
          AND o.origin <> de.tum.cit.aet.hephaestus.practices.model.ResultOrigin.BACKFILL
          AND o.outcome = de.tum.cit.aet.hephaestus.practices.model.Outcome.NOT_MET
        """)
    List<UUID> findPreparedConversationFeedbackIdsForNegativeClaim(
            @Param("workspaceId") Long workspaceId,
            @Param("recipientUserId") Long recipientUserId,
            @Param("practiceId") Long practiceId,
            @Param("artifactKind") ArtifactKind artifactKind,
            @Param("artifactId") Long artifactId);

    /** Newest prepared conversation facts and optional {@link ConversationBriefBody} bodies for a recipient. */
    @Query("""
        SELECT fo.feedback.id AS feedbackId,
               fo.feedback.agentJobId AS agentJobId,
               o.id AS practiceResultId,
               p.slug AS practiceSlug,
               p.name AS practiceName,
               o.summary AS summary,
               o.evidenceRationale AS evidenceRationale,
               o.severity AS severity,
               fo.feedback.body AS body,
               fo.feedback.artifactKind AS artifactKind,
               fo.feedback.artifactId AS artifactId,
               fo.feedback.createdAt AS preparedAt
        FROM FeedbackEvidence fo
        JOIN fo.practiceResult o
        JOIN o.practice p
        WHERE fo.role = de.tum.cit.aet.hephaestus.practices.feedback.EvidenceRole.PRIMARY
          AND fo.feedback.workspaceId = :workspaceId
          AND fo.feedback.recipientUserId = :recipientUserId
          AND fo.feedback.channel = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackChannel.IN_CHAT
          AND fo.feedback.deliveryState = de.tum.cit.aet.hephaestus.practices.feedback.FeedbackDeliveryState.PREPARED
        ORDER BY fo.feedback.createdAt DESC
        """)
    List<PreparedConversationFact> findPreparedConversationFactsForRecipient(
            @Param("workspaceId") Long workspaceId, @Param("recipientUserId") Long recipientUserId, Pageable pageable);

    @Query("""
        SELECT fo.practiceResult.id AS practiceResultId, fo.role AS role, fo.ordinal AS ordinal,
               p.slug AS practiceSlug, p.name AS practiceName, o.summary AS summary,
               pa.slug AS groupSlug, pa.name AS groupName, pa.icon AS groupIcon, pa.color AS groupColor,
               o.outcome AS outcome, o.severity AS severity,
               evaluatedRevision.id AS practiceRevisionId,
               evaluatedRevision.reviewRuleFingerprint AS practiceRevisionFingerprint,
               currentRevision.reviewRuleFingerprint AS currentPracticeRevisionFingerprint,
               o.supersededAt AS supersededAt,
               o.observedAt AS observedAt
        FROM FeedbackEvidence fo
        JOIN fo.practiceResult o
        JOIN o.practice p
        LEFT JOIN o.practiceRevision evaluatedRevision
        LEFT JOIN p.currentRevision currentRevision
        LEFT JOIN p.group pa
        WHERE fo.feedback.id = :feedbackId
          AND fo.feedback.workspaceId = :workspaceId
          AND p.workspace.id = :workspaceId
        ORDER BY fo.ordinal ASC
        """)
    List<BoundResult> findBoundResults(@Param("workspaceId") Long workspaceId, @Param("feedbackId") UUID feedbackId);

    @Query("""
        SELECT DISTINCT fo.feedback.id AS feedbackId, fo.role AS role,
               fo.feedback.agentJobId AS agentJobId, fo.feedback.channel AS channel,
               fo.feedback.deliveryState AS deliveryState, fo.feedback.suppressionReason AS suppressionReason,
               fo.feedback.createdAt AS createdAt, fo.feedback.deliveredAt AS deliveredAt
        FROM FeedbackEvidence fo
        WHERE fo.practiceResult.id = :practiceResultId AND fo.feedback.workspaceId = :workspaceId
        ORDER BY fo.feedback.createdAt DESC, fo.feedback.id DESC
        """)
    List<BoundFeedbackUnit> findBoundFeedbackUnits(
            @Param("workspaceId") Long workspaceId, @Param("practiceResultId") UUID practiceResultId);

    interface BoundResult {
        UUID getPracticeResultId();

        @Nullable
        Instant getSupersededAt();

        EvidenceRole getRole();

        Integer getOrdinal();

        String getPracticeSlug();

        String getPracticeName();

        @Nullable
        String getGroupSlug();

        @Nullable
        String getGroupName();

        @Nullable
        String getGroupIcon();

        @Nullable
        String getGroupColor();

        String getSummary();

        Outcome getOutcome();

        @Nullable
        Severity getSeverity();

        @Nullable
        Long getPracticeRevisionId();

        @Nullable
        String getPracticeRevisionFingerprint();

        @Nullable
        String getCurrentPracticeRevisionFingerprint();

        Instant getObservedAt();
    }

    interface BoundFeedbackUnit {
        UUID getFeedbackId();

        EvidenceRole getRole();

        UUID getAgentJobId();

        FeedbackChannel getChannel();

        FeedbackDeliveryState getDeliveryState();

        @Nullable
        FeedbackSuppressionReason getSuppressionReason();

        Instant getCreatedAt();

        @Nullable
        Instant getDeliveredAt();
    }

    /** Projection: facts + practice for one PREPARED conversational unit (no body - composed at delivery). */
    interface PreparedConversationFact {
        UUID getFeedbackId();

        UUID getAgentJobId();

        UUID getPracticeResultId();

        String getPracticeSlug();

        String getPracticeName();

        String getSummary();

        @Nullable
        String getEvidenceRationale();

        /**
         * The composer's notes to the mentor, or null when nothing was composed for this unit. See
         * {@link ConversationBriefBody}.
         */
        String getBody();

        Severity getSeverity();

        @Nullable
        ArtifactKind getArtifactKind();

        Long getArtifactId();

        Instant getPreparedAt();
    }
}
