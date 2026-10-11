package de.tum.cit.aet.hephaestus.practices.reviewoutput;

import de.tum.cit.aet.hephaestus.integration.scm.domain.repository.MonitoredRepositoryScope;
import de.tum.cit.aet.hephaestus.practices.feedback.Feedback;
import de.tum.cit.aet.hephaestus.practices.feedback.FeedbackRepository;
import de.tum.cit.aet.hephaestus.practices.observation.ObservationRepository;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The reads a lineage makes: observations, feedback and the bindings between them, each scoped to one workspace and
 * each capped by its caller. Read-only, so it extends the bare {@link Repository} and exposes no write.
 */
interface ReviewLineageRepository extends Repository<Feedback, UUID> {

    /** The columns an observation row carries; requires the alias {@code o}. */
    String OBSERVATION_COLUMNS = """
        SELECT o.id AS "id", o.agent_job_id AS "agentJobId", o.summary AS "summary",
               p.slug AS "practiceSlug", p.name AS "practiceName", o.outcome AS "outcome", o.severity AS "severity",
               o.observed_at AS "observedAt", o.artifact_kind AS "artifactKind", o.artifact_id AS "artifactId",
               (SELECT oi.invalidated_at FROM observation_invalidation oi WHERE
        """ + ObservationRepository.OPEN_INVALIDATION + """
               ) AS "markedIncorrectAt",
               linked.feedback_count AS "feedbackCount", linked.own_feedback_count AS "ownFeedbackCount"
        FROM observation o
        JOIN practice p ON p.id = o.practice_id
        CROSS JOIN LATERAL (SELECT COUNT(*) AS feedback_count,
                                   COUNT(*) FILTER (WHERE fb.agent_job_id = o.agent_job_id) AS own_feedback_count
                            FROM feedback_observation fo
                            JOIN feedback fb ON fb.id = fo.feedback_id AND fb.workspace_id = o.workspace_id
                            WHERE fo.observation_id = o.id) linked
        """;

    @Query(value = OBSERVATION_COLUMNS + """
        WHERE o.workspace_id = :workspaceId AND o.id IN (:ids)
        """, nativeQuery = true)
    List<ObservationRow> findObservations(@Param("workspaceId") Long workspaceId, @Param("ids") Collection<UUID> ids);

    /**
     * A review's observations, most worth reading first: the ones feedback came from, then problems by severity, then
     * the rest; newest first within each.
     */
    @Query(value = OBSERVATION_COLUMNS + """
        WHERE o.workspace_id = :workspaceId AND o.agent_job_id = :agentJobId
        ORDER BY EXISTS (SELECT 1 FROM feedback_observation bound WHERE bound.observation_id = o.id) DESC,
                 CASE o.outcome WHEN 'NOT_MET' THEN 0 WHEN 'MET' THEN 1 ELSE 2 END,
                 CASE o.severity WHEN 'CRITICAL' THEN 0 WHEN 'MAJOR' THEN 1 WHEN 'MINOR' THEN 2 WHEN 'INFO' THEN 3
                     ELSE 4 END,
                 o.observed_at DESC, o.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<ObservationRow> findObservationsOfReview(
            @Param("workspaceId") Long workspaceId, @Param("agentJobId") UUID agentJobId, @Param("limit") int limit);

    @Query(value = """
        SELECT o.id FROM observation o WHERE o.workspace_id = :workspaceId AND o.agent_job_id = :agentJobId
        """, nativeQuery = true)
    List<UUID> findObservationIdsOfReview(@Param("workspaceId") Long workspaceId, @Param("agentJobId") UUID agentJobId);

    @Query(value = """
        SELECT COUNT(*) FROM observation o WHERE o.workspace_id = :workspaceId AND o.agent_job_id = :agentJobId
        """, nativeQuery = true)
    int countObservationsOfReview(@Param("workspaceId") Long workspaceId, @Param("agentJobId") UUID agentJobId);

    interface ObservationRow {
        UUID getId();

        UUID getAgentJobId();

        String getSummary();

        String getPracticeSlug();

        String getPracticeName();

        String getOutcome();

        @Nullable
        String getSeverity();

        Instant getObservedAt();

        String getArtifactKind();

        Long getArtifactId();

        /** Set while an admin has the observation marked incorrect; at most one invalidation is open. */
        @Nullable
        Instant getMarkedIncorrectAt();

        long getFeedbackCount();

        long getOwnFeedbackCount();
    }

    /**
     * The columns a feedback row carries; requires the alias {@code f}. The location is the operator list's own
     * expression, so the chain and the list never name a different place for the same piece.
     */
    String FEEDBACK_COLUMNS = """
        SELECT f.id AS "id", f.agent_job_id AS "agentJobId", f.channel AS "channel",
        """ + FeedbackRepository.LOCATION + """
                AS "location",
               a.decision AS "decision", a.decided_at AS "decidedAt",
               (SELECT w.withdrawn_at FROM feedback_withdrawal w
                WHERE w.feedback_id = f.id AND w.workspace_id = f.workspace_id AND w.restored_at IS NULL
                ORDER BY w.withdrawn_at DESC LIMIT 1) AS "withdrawnAt",
               f.delivery_state AS "deliveryState", f.suppression_reason AS "suppressionReason",
               f.created_at AS "createdAt", f.delivered_at AS "deliveredAt", f.replaces_id AS "replacesId",
               f.artifact_kind AS "artifactKind", f.artifact_id AS "artifactId",
               newer.id AS "replacedById", newer.created_at AS "replacedAt",
               (SELECT COUNT(*) FROM feedback_placement fp WHERE fp.feedback_id = f.id) AS "placementCount"
        FROM feedback f
        LEFT JOIN feedback_approval a ON a.feedback_id = f.id AND a.workspace_id = f.workspace_id
        LEFT JOIN LATERAL (SELECT n.id, n.created_at FROM feedback n
                           WHERE n.workspace_id = f.workspace_id AND n.replaces_id = f.id
                           ORDER BY n.created_at DESC, n.id DESC LIMIT 1) newer ON TRUE
        """;

    @Query(value = FEEDBACK_COLUMNS + """
        WHERE f.workspace_id = :workspaceId AND f.id IN (:ids)
        ORDER BY f.created_at DESC, f.id DESC
        """, nativeQuery = true)
    List<FeedbackRow> findFeedback(@Param("workspaceId") Long workspaceId, @Param("ids") Collection<UUID> ids);

    /** The feedback written from one observation, newest first. */
    @Query(value = """
        SELECT f.id FROM feedback_observation fo
        JOIN feedback f ON f.id = fo.feedback_id AND f.workspace_id = :workspaceId
        WHERE fo.observation_id = :observationId
        ORDER BY f.created_at DESC, f.id DESC
        """, nativeQuery = true)
    List<UUID> findFeedbackIdsOfObservation(
            @Param("workspaceId") Long workspaceId, @Param("observationId") UUID observationId);

    /** The feedback one review composed, newest first. */
    @Query(value = """
        SELECT f.id FROM feedback f
        WHERE f.workspace_id = :workspaceId AND f.agent_job_id = :agentJobId
        ORDER BY f.created_at DESC, f.id DESC
        """, nativeQuery = true)
    List<UUID> findFeedbackIdsOfReview(@Param("workspaceId") Long workspaceId, @Param("agentJobId") UUID agentJobId);

    interface FeedbackRow {
        UUID getId();

        UUID getAgentJobId();

        String getChannel();

        String getLocation();

        @Nullable
        String getDecision();

        @Nullable
        Instant getDecidedAt();

        @Nullable
        Instant getWithdrawnAt();

        String getDeliveryState();

        @Nullable
        String getSuppressionReason();

        Instant getCreatedAt();

        @Nullable
        Instant getDeliveredAt();

        @Nullable
        UUID getReplacesId();

        @Nullable
        UUID getReplacedById();

        @Nullable
        Instant getReplacedAt();

        @Nullable
        String getArtifactKind();

        @Nullable
        Long getArtifactId();

        long getPlacementCount();
    }

    /** Which observations each piece of feedback was written from, in render order. */
    @Query(value = """
        SELECT fo.feedback_id AS "feedbackId", fo.observation_id AS "observationId"
        FROM feedback_observation fo
        JOIN feedback f ON f.id = fo.feedback_id AND f.workspace_id = :workspaceId
        WHERE fo.feedback_id IN (:feedbackIds)
        ORDER BY fo.ordinal, fo.observation_id
        """, nativeQuery = true)
    List<BindingRow> findBindings(
            @Param("workspaceId") Long workspaceId, @Param("feedbackIds") Collection<UUID> feedbackIds);

    interface BindingRow {
        UUID getFeedbackId();

        UUID getObservationId();
    }

    /** The newest review that recorded an observation or composed feedback about one piece of work. */
    @Query(value = """
        SELECT recorded.agent_job_id FROM (
            SELECT o.agent_job_id, o.observed_at AS at FROM observation o
            WHERE o.workspace_id = :workspaceId AND o.artifact_kind = :artifactKind AND o.artifact_id = :artifactId
            UNION ALL
            SELECT f.agent_job_id, f.created_at AS at FROM feedback f
            WHERE f.workspace_id = :workspaceId AND f.artifact_kind = :artifactKind AND f.artifact_id = :artifactId
        ) recorded
        ORDER BY recorded.at DESC, recorded.agent_job_id DESC
        LIMIT 1
        """, nativeQuery = true)
    Optional<UUID> findNewestReviewOfWork(
            @Param("workspaceId") Long workspaceId,
            @Param("artifactKind") String artifactKind,
            @Param("artifactId") Long artifactId);

    /**
     * When a pull request or merge request was merged, for one in a repository this workspace monitors. Empty while
     * it is not merged.
     */
    @Query(value = """
        SELECT pr.merged_at FROM issue pr
        WHERE pr.id = :pullRequestId AND pr.issue_type = 'PULL_REQUEST' AND pr.merged_at IS NOT NULL
          AND EXISTS (
              SELECT 1 FROM repository r
              JOIN repository_to_monitor rtm ON rtm.name_with_owner = r.name_with_owner
                AND\s""" + MonitoredRepositoryScope.NATIVE_RTM_R + """

              WHERE r.id = pr.repository_id AND rtm.workspace_id = :workspaceId)
        """, nativeQuery = true)
    Optional<Instant> findMergedAt(@Param("workspaceId") Long workspaceId, @Param("pullRequestId") Long pullRequestId);

    /**
     * The first moment on the work that names the review: the one that started it. A later moment that names it was
     * folded into the same review.
     */
    @Query(value = """
        SELECT s.id AS "id", s.signal_name AS "signalName", s.discovered_via AS "discoveredVia",
               s.occurred_at AS "occurredAt"
        FROM artifact_signal s
        WHERE s.workspace_id = :workspaceId AND s.artifact_kind = :artifactKind AND s.artifact_id = :artifactId
          AND s.job_id = :agentJobId
        ORDER BY s.occurred_at, s.id
        LIMIT 1
        """, nativeQuery = true)
    Optional<StartRow> findStart(
            @Param("workspaceId") Long workspaceId,
            @Param("artifactKind") String artifactKind,
            @Param("artifactId") Long artifactId,
            @Param("agentJobId") UUID agentJobId);

    interface StartRow {
        UUID getId();

        String getSignalName();

        String getDiscoveredVia();

        Instant getOccurredAt();
    }

    /**
     * The newest moments on one piece of work, newest first, and the review each started or was folded into, if any.
     * Several moments can name one review: the first started it, the rest joined it.
     */
    @Query(value = """
        SELECT s.id AS "id", s.signal_name AS "signalName", s.discovered_via AS "discoveredVia",
               s.occurred_at AS "occurredAt", s.job_id AS "jobId"
        FROM artifact_signal s
        WHERE s.workspace_id = :workspaceId AND s.artifact_kind = :artifactKind AND s.artifact_id = :artifactId
        ORDER BY s.occurred_at DESC, s.id DESC
        LIMIT :limit
        """, nativeQuery = true)
    List<MomentRow> findMoments(
            @Param("workspaceId") Long workspaceId,
            @Param("artifactKind") String artifactKind,
            @Param("artifactId") Long artifactId,
            @Param("limit") int limit);

    interface MomentRow extends StartRow {
        @Nullable
        UUID getJobId();
    }

    /** The feedback rows themselves, for the reads that judge whether later work resolved them. */
    @Query("SELECT f FROM Feedback f WHERE f.workspaceId = :workspaceId AND f.id IN :ids")
    List<Feedback> findAllByWorkspaceIdAndIdIn(
            @Param("workspaceId") Long workspaceId, @Param("ids") Collection<UUID> ids);
}
