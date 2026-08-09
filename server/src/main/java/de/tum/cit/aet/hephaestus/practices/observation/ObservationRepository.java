package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.signal.ArtifactKind;
import de.tum.cit.aet.hephaestus.practices.model.ArtifactKinds;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin;
import de.tum.cit.aet.hephaestus.practices.model.PracticeReviewTier;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for immutable practice findings with idempotent insertion.
 *
 * <p>Workspace-agnostic: findings are scoped through {@code Practice.workspace}
 * relationship, not via a direct workspace_id column.
 */
@Repository
@WorkspaceAgnostic("Findings scoped through Practice.workspace relationship")
public interface ObservationRepository extends JpaRepository<Observation, UUID> {
    @EntityGraph(attributePaths = { "practice.currentRevision", "practiceRevision" })
    @Query("SELECT f FROM Observation f JOIN f.practice p WHERE f.id = :id AND p.workspace.id = :workspaceId")
    Optional<Observation> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") Long workspaceId);

    /**
     * All findings a given agent job produced — the source set the feedback ledger recorder binds to.
     * Ordered by id so {@code get(0)} is deterministic across retries: the recorder derives the recipient,
     * artifact, and thread key from the first row, and an unordered read could re-source them differently on
     * a re-run of a multi-subject / multi-artifact job.
     */
    @Query("SELECT f FROM Observation f WHERE f.agentJobId = :agentJobId ORDER BY f.id ASC")
    List<Observation> findByAgentJobId(@Param("agentJobId") UUID agentJobId);

    @Query(
        value = """
        SELECT o.agent_job_id AS "jobId",
               COUNT(*) FILTER (WHERE o.assessment = 'GOOD') AS "strengths",
               COUNT(*) FILTER (WHERE o.assessment = 'BAD') AS "problems",
               COUNT(*) FILTER (WHERE o.presence = 'NOT_APPLICABLE') AS "notApplicable",
               COUNT(*) FILTER (WHERE o.presence = 'INDETERMINATE') AS "indeterminate"
        FROM observation o
        JOIN practice p ON p.id = o.practice_id
        WHERE p.workspace_id = :workspaceId
          AND o.agent_job_id IN :jobIds
        GROUP BY o.agent_job_id
        """,
        nativeQuery = true
    )
    List<ReviewFindingCounts> summarizeReviewFindings(
        @Param("workspaceId") Long workspaceId,
        @Param("jobIds") Collection<UUID> jobIds
    );

    interface ReviewFindingCounts {
        UUID getJobId();
        Long getStrengths();
        Long getProblems();
        Long getNotApplicable();
        /**
         * Runs where the practice looked and could not settle the question. Counted apart from
         * {@link #getNotApplicable()}: both are silence on the artifact, but an operator reading a review
         * summary needs "nothing here to judge" told apart from "we could not tell".
         */
        Long getIndeterminate();
    }

    /**
     * Atomically inserts a practice finding if absent (race-condition safe).
     *
     * <p>Uses PostgreSQL's ON CONFLICT DO NOTHING to handle concurrent inserts.
     * This avoids the race condition where exists() check passes but save() fails
     * with DataIntegrityViolationException at transaction commit time.
     *
     * @return 1 if inserted, 0 if duplicate (conflict on occurrence_key)
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO observation (
            id, occurrence_key, agent_job_id, practice_id, practice_revision_id,
            artifact_kind, artifact_id, about_user_id,
            title, presence, assessment, severity, confidence,
            evidence, reasoning,
            recurrence_key, observed_at, origin
        )
        VALUES (
            :id, :idempotencyKey, :agentJobId, :practiceId, :practiceRevisionId,
            :artifactKind, :artifactId, :aboutUserId,
            :title, :presence, :assessment, :severity, :confidence,
            CAST(:evidence AS jsonb), :reasoning,
            :recurrenceKey, :observedAt, :origin
        )
        ON CONFLICT (occurrence_key) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("agentJobId") UUID agentJobId,
        @Param("practiceId") Long practiceId,
        @Param("practiceRevisionId") Long practiceRevisionId,
        @Param("artifactKind") String artifactKind,
        @Param("artifactId") Long artifactId,
        @Param("aboutUserId") Long aboutUserId,
        @Param("title") String title,
        @Param("presence") String presence,
        @Param("assessment") String assessment,
        @Param("severity") String severity,
        @Param("confidence") Float confidence,
        @Param("evidence") String evidence,
        @Param("reasoning") String reasoning,
        @Param("recurrenceKey") String recurrenceKey,
        @Param("observedAt") Instant observedAt,
        @Param("origin") String origin
    );

    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM observation WHERE practice_id IN (SELECT id FROM practice WHERE workspace_id = :workspaceId)",
        nativeQuery = true
    )
    void deleteAllByPracticeWorkspaceId(@Param("workspaceId") Long workspaceId);

    /**
     * Hard-delete the {@code chat.conversation_thread} observations for a workspace whose {@code artifact_id} (the
     * {@code slack_thread} id) is one of {@code artifactIds} — the derived-content erasure the Slack module invokes
     * through {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure} when a channel's consent is
     * withdrawn. Workspace is scoped through the {@code Practice.workspace} relationship (this repo is
     * {@code @WorkspaceAgnostic}); the {@code artifactKind} + {@code artifactId} predicates keep PR/ISSUE observations
     * and other tenants' rows untouched. DB {@code ON DELETE CASCADE} clears any bound {@code feedback_observation} /
     * {@code reaction} children. Callers guard an empty {@code artifactIds}.
     *
     * @return the number of observations deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Observation o
        WHERE o.artifactKind = :artifactKind
          AND o.artifactId IN :artifactIds
          AND o.practice.id IN (SELECT p.id FROM Practice p WHERE p.workspace.id = :workspaceId)
        """
    )
    int deleteObservationsOfKind(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactKind") ArtifactKind artifactKind,
        @Param("artifactIds") Collection<Long> artifactIds
    );

    default int deleteConversationThreadObservations(Long workspaceId, Collection<Long> artifactIds) {
        return deleteObservationsOfKind(workspaceId, ArtifactKinds.CONVERSATION_THREAD, artifactIds);
    }

    /**
     * Hard-delete <em>every</em> {@code chat.conversation_thread} observation for a workspace — the whole-tenant erasure
     * the Slack module invokes through
     * {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure#eraseAllConversationForWorkspace} on
     * app-uninstall / workspace-purge. Workspace is scoped through the {@code Practice.workspace} relationship (this
     * repo is {@code @WorkspaceAgnostic}); the {@code artifactKind} predicate keeps PR/ISSUE observations and other
     * tenants' rows untouched. DB {@code ON DELETE CASCADE} clears any bound {@code feedback_observation} /
     * {@code reaction} children. Idempotent.
     *
     * @return the number of observations deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Observation o
        WHERE o.artifactKind IN :artifactKinds
          AND o.practice.id IN (SELECT p.id FROM Practice p WHERE p.workspace.id = :workspaceId)
        """
    )
    int deleteAllObservationsOfKinds(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactKinds") Collection<ArtifactKind> artifactKinds
    );

    default int deleteAllConversationThreadObservations(Long workspaceId) {
        return deleteAllObservationsOfKinds(workspaceId, List.of(ArtifactKinds.CONVERSATION_THREAD));
    }

    /**
     * Hard-delete every {@code scm.pull_request} / {@code scm.issue} observation for a workspace — the
     * SCM-derived counterpart of {@link #deleteAllConversationThreadObservations}, invoked when the
     * workspace's SCM mirror is erased on connection-disconnect or workspace-purge. The
     * {@code evidence} jsonb quotes mirrored diff/comment content verbatim and {@code artifact_id} is
     * a soft reference (no FK to {@code issue}/{@code pull_request}), so these rows would otherwise
     * outlive the artifacts they describe. Workspace is scoped through the {@code Practice.workspace}
     * relationship (this repo is {@code @WorkspaceAgnostic}); the {@code artifactKind} predicate keeps
     * {@code chat.conversation_thread} observations and other tenants' rows untouched. DB
     * {@code ON DELETE CASCADE} clears any bound {@code feedback_observation} / {@code reaction}
     * children. Idempotent.
     *
     * @return the number of observations deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Observation o
        WHERE o.artifactKind IN :artifactKinds
          AND o.practice.id IN (SELECT p.id FROM Practice p WHERE p.workspace.id = :workspaceId)
        """
    )
    int deleteAllScmObservationsOfKinds(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactKinds") Collection<ArtifactKind> artifactKinds
    );

    default int deleteAllScmArtifactObservations(Long workspaceId) {
        return deleteAllScmObservationsOfKinds(workspaceId, List.of(ArtifactKinds.PULL_REQUEST, ArtifactKinds.ISSUE));
    }

    /**
     * Hard-delete the {@code chat.conversation_thread} observations a single person is the <em>subject</em> of
     * ({@code about_user_id = :aboutUserId}) within a workspace — the derived-content half of a person opt-out /
     * account hard-delete, invoked through
     * {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure#eraseConversationFeedbackAboutUser}.
     * Workspace is scoped through {@code Practice.workspace}; the {@code artifactKind} + {@code aboutUserId}
     * predicates keep another person's rows, PR/ISSUE observations, and other tenants' rows intact. DB
     * {@code ON DELETE CASCADE} clears any bound {@code feedback_observation} / {@code reaction} children. Idempotent.
     *
     * @return the number of observations deleted
     */
    @Modifying
    @Transactional
    @Query(
        """
        DELETE FROM Observation o
        WHERE o.artifactKind = :artifactKind
          AND o.aboutUserId = :aboutUserId
          AND o.practice.id IN (SELECT p.id FROM Practice p WHERE p.workspace.id = :workspaceId)
        """
    )
    int deleteObservationsOfKindAboutUser(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactKind") ArtifactKind artifactKind,
        @Param("aboutUserId") Long aboutUserId
    );

    default int deleteConversationThreadObservationsAboutUser(Long workspaceId, Long aboutUserId) {
        return deleteObservationsOfKindAboutUser(workspaceId, ArtifactKinds.CONVERSATION_THREAD, aboutUserId);
    }

    // Read queries for the developer dashboard (Issue #896)

    /**
     * Paginated findings for an about-user within a workspace, with optional filters.
     *
     * <p>Workspace scoping is done via the {@code Practice.workspace} join. The about-user is the
     * {@code about_user_id} subject the finding is filed against (ADR 0022).
     * Uses a separate {@code countQuery} because {@code JOIN FETCH} is incompatible
     * with count projections in Hibernate.
     */
    @EntityGraph(attributePaths = { "practice.currentRevision", "practiceRevision" })
    @Query(
        value = """
        SELECT f FROM Observation f
        JOIN FETCH f.practice p
        WHERE f.aboutUserId = :aboutUserId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:presence IS NULL OR f.presence = :presence)
        """,
        countQuery = """
        SELECT COUNT(f) FROM Observation f
        JOIN f.practice p
        WHERE f.aboutUserId = :aboutUserId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:presence IS NULL OR f.presence = :presence)
        """
    )
    Page<Observation> findByAboutUserAndWorkspace(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId,
        @Param("practiceSlug") String practiceSlug,
        @Param("presence") Presence presence,
        Pageable pageable
    );

    /**
     * Per-practice aggregation for the developer dashboard: present/good and bad counts, and last finding date.
     *
     * <p>Aggregates represent each target's current state: within the workspace, only the run with the newest
     * {@code (observed_at, agent_job_id)} tuple contributes.
     *
     * <p>Aggregate views have no team context, so a repository hidden by any workspace team is excluded. Raw
     * per-artifact fetches remain unfiltered.
     *
     * <p>Native (not JPQL) because the latest-run-per-target selection needs {@code ORDER BY ... LIMIT 1} in a
     * correlated subquery, which JPQL cannot express. Aliases are quoted so the JDBC column labels match the
     * {@link DeveloperPracticeSummaryProjection} getters exactly (Postgres folds unquoted identifiers to
     * lower-case). Enum columns compare against their {@code STRING} storage form. {@code goodCount} is
     * the strengths ({@code assessment='GOOD'}); {@code badCount} is the problems ({@code assessment='BAD'}).
     */
    @Query(
        value = """
        SELECT p.slug AS "practiceSlug",
               p.name AS "practiceName",
               COUNT(f.id) AS "totalObservations",
               SUM(CASE WHEN f.assessment = 'GOOD' THEN 1 ELSE 0 END) AS "goodCount",
               SUM(CASE WHEN f.assessment = 'BAD' THEN 1 ELSE 0 END) AS "badCount",
               MAX(f.observed_at) AS "lastObservedAt"
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.about_user_id = :aboutUserId
          AND p.workspace_id = :workspaceId
          AND NOT EXISTS (
              SELECT 1
              FROM issue target_artifact
              JOIN workspace_team_repository_settings wtrs
                ON wtrs.workspace_id = p.workspace_id
               AND wtrs.repository_id = target_artifact.repository_id
               AND wtrs.hidden_from_contributions = true
              WHERE f.artifact_kind IN ('scm.pull_request', 'scm.issue')
                AND target_artifact.id = f.artifact_id
          )
          AND f.origin <> 'BACKFILL'
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_kind = f.artifact_kind
                AND f2.artifact_id = f.artifact_id
                AND f2.origin <> 'BACKFILL'
              ORDER BY f2.observed_at DESC, f2.agent_job_id DESC
              LIMIT 1
          )
        GROUP BY p.slug, p.name
        ORDER BY p.name ASC
        """,
        nativeQuery = true
    )
    List<DeveloperPracticeSummaryProjection> findSummaryByDeveloperAndWorkspace(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Single finding by ID within a workspace, restricted to a specific about-user.
     *
     * <p>Ownership is enforced in the query (not in Java) to avoid lazy-load
     * fragility and to keep the auth check atomic with the fetch.
     */
    @EntityGraph(attributePaths = { "practice.currentRevision", "practiceRevision" })
    @Query(
        """
        SELECT f FROM Observation f
        JOIN FETCH f.practice p
        WHERE f.id = :observationId
        AND f.aboutUserId = :aboutUserId
        AND p.workspace.id = :workspaceId
        """
    )
    Optional<Observation> findByIdAndDeveloperAndWorkspace(
        @Param("observationId") UUID observationId,
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * All findings for a specific pull request within a workspace.
     */
    @EntityGraph(attributePaths = { "practice.currentRevision", "practiceRevision" })
    @Query(
        """
        SELECT f FROM Observation f
        JOIN FETCH f.practice p
        WHERE f.artifactKind = :artifactKind
        AND f.artifactId = :pullRequestId
        AND p.workspace.id = :workspaceId
        ORDER BY f.observedAt DESC
        """
    )
    List<Observation> findByPullRequestAndWorkspace(
        @Param("artifactKind") ArtifactKind artifactKind,
        @Param("pullRequestId") Long pullRequestId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Recent findings the mentor can refer to by title in conversation.
     *
     * <p>Re-review deduped (same grain as {@link #findSummaryByDeveloperAndWorkspace}): keeps only each
     * target's LATEST detection run, so a re-pushed PR's findings don't repeat across the list and the
     * mentor doesn't see (and re-litigate) the same finding four times. Native because the latest-run
     * selection needs {@code ORDER BY ... LIMIT 1} in a correlated subquery. The practice is loaded lazily
     * per finding (bounded by the page size) rather than JOIN-fetched.
     *
     * <p>Only a presence that {@link Presence#carriesValence() carries valence} is listed. {@code NOT_APPLICABLE}
     * dominates the list ("no change needed / awaiting review" rows) and would bury the actionable {@code BAD}
     * problems and {@code GOOD} strengths within the page budget; {@code INDETERMINATE} is excluded because
     * coaching on it would invite the mentor to invent a direction the measurement declined to take. Both
     * totals still reach the mentor via the presence-count summary; this is the drill-down list only, and
     * stays recency-ordered (NOT re-ordered by severity) to preserve its "what happened lately" purpose.
     *
     * <p><strong>Backfilled observations are included, partitioned by origin class.</strong> A campaign spends
     * real money reviewing work that already existed, and a {@code BAD} it found on the developer's own pull
     * request is exactly what "what should I work on" is asking for. Excluding it made the campaign produce
     * something no developer could see anywhere. The latest-run correlation stays, but is evaluated
     * <em>within</em> each origin class ({@code (f2.origin = 'BACKFILL') = (f.origin = 'BACKFILL')}) rather
     * than over the union, for two reasons:
     * <ul>
     *   <li>Dropping the outer predicate alone would be a no-op — the row must still equal the latest
     *       <em>non-backfill</em> job id, which a backfilled row never does.</li>
     *   <li>Making the correlation origin-blind would let a campaign <em>erase</em> already-delivered live
     *       feedback from the dashboard, because the campaign's job would become "the latest run".</li>
     * </ul>
     * The two classes cannot in practice describe the same occasion: a campaign keys its ledger entry with the
     * same revision derivation as the live path, so {@code uq_artifact_signal} makes it skip any artifact the
     * live path already reviewed at that occasion. {@code ReflectionItemDTO.origin()} carries the class through
     * so the surface can label a backfilled item rather than pass it off as live.
     *
     * <p>Aggregate policy deliberately DIVERGES from {@link #findSummaryByDeveloperAndWorkspace} here: the
     * summary is a per-practice good/bad ratio read as a trend, and a hindsight campaign is not a point on a
     * trend line.
     */
    @Query(
        value = """
        SELECT f.* FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.about_user_id = :aboutUserId
          AND p.workspace_id = :workspaceId
          AND NOT EXISTS (
              SELECT 1
              FROM issue target_artifact
              JOIN workspace_team_repository_settings wtrs
                ON wtrs.workspace_id = p.workspace_id
               AND wtrs.repository_id = target_artifact.repository_id
               AND wtrs.hidden_from_contributions = true
              WHERE f.artifact_kind IN ('scm.pull_request', 'scm.issue')
                AND target_artifact.id = f.artifact_id
          )
          AND f.observed_at >= :since
          AND f.presence IN ('PRESENT', 'ABSENT')
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_kind = f.artifact_kind AND f2.artifact_id = f.artifact_id
                AND (f2.origin = 'BACKFILL') = (f.origin = 'BACKFILL')
              ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
          )
        ORDER BY f.observed_at DESC
        """,
        nativeQuery = true
    )
    List<Observation> findRecentByDeveloperAndWorkspace(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        Pageable pageable
    );

    /**
     * Severity histogram for a developer's findings within a workspace.
     * Returns {@code [severityName, count]} rows — caller maps to a name→count map.
     *
     * <p>Re-review deduped to each target's latest run (see {@link #findRecentByDeveloperAndWorkspace}) so
     * the mentor's "how am I doing" histogram reflects current state, not the re-push multiplier. Only
     * {@code BAD} findings carry a non-null severity, so the histogram is over problems.
     */
    @Query(
        value = """
        SELECT f.severity AS severity, COUNT(f.id) AS count
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.about_user_id = :aboutUserId
          AND p.workspace_id = :workspaceId
          AND NOT EXISTS (
              SELECT 1
              FROM issue target_artifact
              JOIN workspace_team_repository_settings wtrs
                ON wtrs.workspace_id = p.workspace_id
               AND wtrs.repository_id = target_artifact.repository_id
               AND wtrs.hidden_from_contributions = true
              WHERE f.artifact_kind IN ('scm.pull_request', 'scm.issue')
                AND target_artifact.id = f.artifact_id
          )
          AND f.observed_at >= :since
          AND f.severity IS NOT NULL
          AND f.origin <> 'BACKFILL'
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_kind = f.artifact_kind AND f2.artifact_id = f.artifact_id
                AND f2.origin <> 'BACKFILL'
              ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
          )
        GROUP BY f.severity
        """,
        nativeQuery = true
    )
    List<SeverityCount> countBySeverityForDeveloper(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since
    );

    /**
     * Presence histogram for a developer's findings within a workspace.
     *
     * <p>Aggregate policy matches {@link #findSummaryByDeveloperAndWorkspace}.
     */
    @Query(
        value = """
        SELECT f.presence AS presence, COUNT(f.id) AS count
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.about_user_id = :aboutUserId
          AND p.workspace_id = :workspaceId
          AND NOT EXISTS (
              SELECT 1
              FROM issue target_artifact
              JOIN workspace_team_repository_settings wtrs
                ON wtrs.workspace_id = p.workspace_id
               AND wtrs.repository_id = target_artifact.repository_id
               AND wtrs.hidden_from_contributions = true
              WHERE f.artifact_kind IN ('scm.pull_request', 'scm.issue')
                AND target_artifact.id = f.artifact_id
          )
          AND f.observed_at >= :since
          AND f.origin <> 'BACKFILL'
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_kind = f.artifact_kind AND f2.artifact_id = f.artifact_id
                AND f2.origin <> 'BACKFILL'
              ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
          )
        GROUP BY f.presence
        """,
        nativeQuery = true
    )
    List<PresenceCount> countByPresenceForDeveloper(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since
    );

    // Cross-run trend read path (ADR 0021, A1) — the measurement substrate ObservationTrendService classifies.

    /**
     * The runs (agent jobs) that produced ≥1 correlation-keyed finding for a target, newest first by the
     * run's latest detection. Pass {@code PageRequest.of(0, 2)} to get the two most-recent runs to diff.
     * Workspace-scoped via {@code Practice.workspace}.
     *
     * <p>A {@code BACKFILL} run is never one of the two. The diff is read as "did this get fixed between
     * the two times we looked", and a sweep that looked at the artifact long after the fact would answer
     * that question with a difference in sampling rather than a difference in the work.
     */
    @Query(
        """
        SELECT f.agentJobId AS agentJobId, MAX(f.observedAt) AS runAt
        FROM Observation f JOIN f.practice p
        WHERE f.artifactKind = :artifactKind AND f.artifactId = :artifactId AND p.workspace.id = :workspaceId
          AND f.recurrenceKey IS NOT NULL
          AND f.origin <> de.tum.cit.aet.hephaestus.practices.model.ObservationOrigin.BACKFILL
        GROUP BY f.agentJobId
        ORDER BY MAX(f.observedAt) DESC, f.agentJobId DESC
        """
    )
    List<RunRef> findRecentRunRefsForTarget(
        @Param("artifactKind") ArtifactKind artifactKind,
        @Param("artifactId") Long artifactId,
        @Param("workspaceId") Long workspaceId,
        Pageable pageable
    );

    /** All correlation-keyed findings for the given (already-resolved) run job-ids, with the trend fields. */
    @Query(
        """
        SELECT f.agentJobId AS agentJobId, f.recurrenceKey AS recurrenceKey, f.presence AS presence,
               f.assessment AS assessment, f.severity AS severity, f.confidence AS confidence, p.slug AS practiceSlug,
               f.title AS title, f.observedAt AS observedAt
        FROM Observation f JOIN f.practice p
        WHERE f.agentJobId IN :agentJobIds AND p.workspace.id = :workspaceId AND f.recurrenceKey IS NOT NULL
          AND f.presence IN (de.tum.cit.aet.hephaestus.practices.model.Presence.PRESENT,
                             de.tum.cit.aet.hephaestus.practices.model.Presence.ABSENT)
        ORDER BY f.observedAt DESC
        """
    )
    List<LocusObservation> findLociByAgentJobs(
        @Param("agentJobIds") Collection<UUID> agentJobIds,
        @Param("workspaceId") Long workspaceId
    );

    /** Projection: one run (agent job) with its latest detection timestamp. */
    interface RunRef {
        UUID getAgentJobId();
        Instant getRunAt();
    }

    /**
     * Projection: the locus-identity + sign fields the trend classifier keys on (which run, which
     * recurrence locus, present-or-not, good-or-bad). Split out from {@link LocusObservation} so each
     * projection stays focused (ISP); {@link LocusObservation} extends it with the presentation fields.
     */
    interface LocusKey {
        UUID getAgentJobId();
        String getRecurrenceKey();
        Presence getPresence();
        Assessment getAssessment();
    }

    /** Projection: a correlation-keyed observation reduced to the fields the trend classifier needs. */
    interface LocusObservation extends LocusKey {
        Severity getSeverity();
        Float getConfidence();
        String getPracticeSlug();
        String getTitle();
        Instant getObservedAt();
    }

    /** Projection: severity → count. */
    interface SeverityCount {
        Severity getSeverity();
        Long getCount();
    }

    /** Projection: presence → count. */
    interface PresenceCount {
        Presence getPresence();
        Long getCount();
    }

    String OPERATOR_PREDICATES = """
          AND (CAST(:#{#f.practiceSlugArray()} AS text[]) IS NULL OR p.slug = ANY(CAST(:#{#f.practiceSlugArray()} AS text[])))
          AND (CAST(:#{#f.areaSlugArray()} AS text[]) IS NULL OR pa.slug = ANY(CAST(:#{#f.areaSlugArray()} AS text[])))
          AND (CAST(:#{#f.presenceNames()} AS text[]) IS NULL OR o.presence = ANY(CAST(:#{#f.presenceNames()} AS text[])))
          AND (CAST(:#{#f.assessmentNames()} AS text[]) IS NULL OR o.assessment = ANY(CAST(:#{#f.assessmentNames()} AS text[])))
          AND (CAST(:#{#f.severityNames()} AS text[]) IS NULL OR o.severity = ANY(CAST(:#{#f.severityNames()} AS text[])))
          AND (CAST(:#{#f.agentJobId()} AS uuid) IS NULL OR o.agent_job_id = CAST(:#{#f.agentJobId()} AS uuid))
          AND (CAST(:#{#f.artifactKindValue()} AS text) IS NULL OR o.artifact_kind = CAST(:#{#f.artifactKindValue()} AS text))
          AND (CAST(:#{#f.artifactId()} AS bigint) IS NULL OR o.artifact_id = CAST(:#{#f.artifactId()} AS bigint))
          AND (CAST(:#{#f.aboutUserId()} AS bigint) IS NULL OR o.about_user_id = CAST(:#{#f.aboutUserId()} AS bigint))
          AND (CAST(:#{#f.originNames()} AS text[]) IS NULL OR o.origin = ANY(CAST(:#{#f.originNames()} AS text[])))
          AND (CAST(:#{#f.from()} AS timestamptz) IS NULL OR o.observed_at >= CAST(:#{#f.from()} AS timestamptz))
          AND (CAST(:#{#f.to()} AS timestamptz) IS NULL OR o.observed_at < CAST(:#{#f.to()} AS timestamptz))
        """;

    @Query(
        value = """
            SELECT o.id AS "id",
                   o.agent_job_id AS "agentJobId",
                   p.slug AS "practiceSlug",
                   p.name AS "practiceName",
                   pa.slug AS "areaSlug",
                   pa.name AS "areaName",
                   pa.icon AS "areaIcon",
                   pa.color AS "areaColor",
                   o.artifact_kind AS "artifactKind",
                   o.artifact_id AS "artifactId",
                   o.about_user_id AS "aboutUserId",
                   o.title AS "title",
                   o.presence AS "presence",
                   o.assessment AS "assessment",
                   o.severity AS "severity",
                   o.confidence AS "confidence",
                   o.recurrence_key AS "recurrenceKey",
                   o.origin AS "origin",
                   o.practice_revision_id AS "practiceRevisionId",
                   evaluated_revision.review_rule_fingerprint AS "practiceRevisionFingerprint",
                   current_revision.review_rule_fingerprint AS "currentPracticeRevisionFingerprint",
                   o.observed_at AS "observedAt"
            FROM observation o
            JOIN practice p ON p.id = o.practice_id
            LEFT JOIN practice_revision evaluated_revision ON evaluated_revision.id = o.practice_revision_id
            LEFT JOIN practice_revision current_revision ON current_revision.id = p.current_revision_id
            LEFT JOIN practice_area pa ON pa.id = p.practice_area_id
            WHERE p.workspace_id = :workspaceId
            """ +
            OPERATOR_PREDICATES +
            """
             ORDER BY
               CASE WHEN :prioritizeActionable THEN
                 CASE o.assessment WHEN 'BAD' THEN 0 WHEN 'GOOD' THEN 1 ELSE 2 END
               ELSE 0 END,
               CASE WHEN :prioritizeActionable AND o.assessment = 'BAD' THEN
                 CASE o.severity
                   WHEN 'CRITICAL' THEN 0
                   WHEN 'MAJOR' THEN 1
                   WHEN 'MINOR' THEN 2
                   WHEN 'INFO' THEN 3
                   ELSE 4
                 END
               ELSE 0 END,
               o.observed_at DESC,
               o.id DESC
            """,
        countQuery = """
            SELECT count(*)
            FROM observation o
            JOIN practice p ON p.id = o.practice_id
            LEFT JOIN practice_area pa ON pa.id = p.practice_area_id
            WHERE p.workspace_id = :workspaceId
            """ +
            OPERATOR_PREDICATES,
        nativeQuery = true
    )
    Page<OperatorObservationRow> findForWorkspace(
        @Param("workspaceId") Long workspaceId,
        @Param("f") ObservationQueryFilter filter,
        @Param("prioritizeActionable") boolean prioritizeActionable,
        Pageable pageable
    );

    interface OperatorObservationRow {
        UUID getId();
        UUID getAgentJobId();
        String getPracticeSlug();
        String getPracticeName();
        String getAreaSlug();
        String getAreaName();
        String getAreaIcon();
        String getAreaColor();
        /** The raw column: a native-query projection is mapped from JDBC types, with no converter run. */
        String getArtifactKind();
        Long getArtifactId();
        Long getAboutUserId();
        String getTitle();
        Presence getPresence();
        Assessment getAssessment();
        Severity getSeverity();
        Float getConfidence();
        String getRecurrenceKey();
        /**
         * What occasioned the measurement. Without it the operator surface cannot tell a campaign's findings
         * from live ones, which is a population-mixing hazard in exactly the surface used to judge whether a
         * campaign was worth its cost.
         */
        ObservationOrigin getOrigin();
        Long getPracticeRevisionId();
        String getPracticeRevisionFingerprint();
        String getCurrentPracticeRevisionFingerprint();
        Instant getObservedAt();
    }

    @Query(
        value = """
        SELECT fo.observation_id AS "observationId",
               COUNT(*) FILTER (WHERE f.delivery_state = 'PREPARED') AS "prepared",
               COUNT(*) FILTER (WHERE f.delivery_state = 'DELIVERED') AS "delivered",
               COUNT(*) FILTER (WHERE f.delivery_state = 'SUPERSEDED') AS "superseded",
               COUNT(*) FILTER (WHERE f.delivery_state = 'SUPPRESSED') AS "suppressed",
               COUNT(*) FILTER (WHERE f.delivery_state = 'FAILED') AS "failed"
        FROM feedback_observation fo
        JOIN feedback f ON f.id = fo.feedback_id
        WHERE fo.observation_id IN :observationIds
          AND f.workspace_id = :workspaceId
        GROUP BY fo.observation_id
        """,
        nativeQuery = true
    )
    List<ObservationFeedbackDisposition> findFeedbackDispositions(
        @Param("workspaceId") Long workspaceId,
        @Param("observationIds") Collection<UUID> observationIds
    );

    interface ObservationFeedbackDisposition {
        UUID getObservationId();
        Long getPrepared();
        Long getDelivered();
        Long getSuperseded();
        Long getSuppressed();
        Long getFailed();
    }

    /**
     * The loudness tier of each observation's practice, resolved in ONE query rather than by walking the
     * lazy {@code Observation.practice} association.
     *
     * <p>The association is deliberately not used: the conversational router receives observations that
     * may already be detached, so traversing a lazy proxy there would make the tier rule's correctness
     * depend on whether the caller happens to hold a session. A projection is session-independent.
     */
    @Query(
        """
        SELECT o.id AS observationId, o.practice.reviewTier AS reviewTier
        FROM Observation o
        WHERE o.id IN :observationIds
        """
    )
    List<ObservationPracticeTier> practiceReviewTiersFor(@Param("observationIds") Collection<UUID> observationIds);

    /** One observation's practice tier, without loading either entity. */
    interface ObservationPracticeTier {
        UUID getObservationId();
        PracticeReviewTier getReviewTier();
    }

    /**
     * Every measurement taken on one artifact, by practice — the trace view's evidence that a practice
     * ran at all.
     *
     * <p>Keyed off the artifact rather than off a run: a practice that produced a measurement must never
     * be reported as silent, including when its run predates the signal ledger or was never linked back.
     */
    @Query(
        """
        SELECT o.practice.id AS practiceId, o.agentJobId AS reviewId, o.observedAt AS observedAt
        FROM Observation o
        WHERE o.practice.workspace.id = :workspaceId
          AND o.artifactKind = :artifactKind
          AND o.artifactId = :artifactId
        """
    )
    List<ArtifactObservationRow> findForArtifact(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactKind") ArtifactKind artifactKind,
        @Param("artifactId") Long artifactId
    );

    interface ArtifactObservationRow {
        Long getPracticeId();
        UUID getReviewId();
        Instant getObservedAt();
    }
}
