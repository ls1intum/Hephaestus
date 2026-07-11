package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
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

/**
 * Repository for immutable practice findings with idempotent insertion.
 *
 * <p>Workspace-agnostic: findings are scoped through {@code Practice.workspace}
 * relationship, not via a direct workspace_id column.
 */
@Repository
@WorkspaceAgnostic("Findings scoped through Practice.workspace relationship")
public interface ObservationRepository extends JpaRepository<Observation, UUID> {
    /**
     * Finds a practice finding by ID, scoped to a specific workspace.
     * Used to validate that a finding belongs to the caller's workspace before allowing operations on it.
     */
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

    /**
     * Job observations EXCLUDING the given practice slugs, ordered by id (see {@link #findByAgentJobId}).
     * Used by the in-context feedback ledger to fuse ONLY what was actually delivered to the artifact's
     * page: reviewer-audience findings are persisted but firewalled OUT of the author's delivery, so they
     * must never be linked into the author's IN_CONTEXT {@code Feedback} unit (ADR 0021 C2).
     */
    @Query(
        "SELECT f FROM Observation f WHERE f.agentJobId = :agentJobId AND f.practice.slug NOT IN :excludedSlugs ORDER BY f.id ASC"
    )
    List<Observation> findByAgentJobIdExcludingSlugs(
        @Param("agentJobId") UUID agentJobId,
        @Param("excludedSlugs") Collection<String> excludedSlugs
    );

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
            artifact_type, artifact_id, about_user_id,
            title, presence, assessment, severity, confidence,
            evidence, reasoning,
            recurrence_key, observed_at
        )
        VALUES (
            :id, :idempotencyKey, :agentJobId, :practiceId, :practiceRevisionId,
            :artifactType, :artifactId, :aboutUserId,
            :title, :presence, :assessment, :severity, :confidence,
            CAST(:evidence AS jsonb), :reasoning,
            :recurrenceKey, :observedAt
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
        @Param("artifactType") String artifactType,
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
        @Param("observedAt") Instant observedAt
    );

    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM observation WHERE practice_id IN (SELECT id FROM practice WHERE workspace_id = :workspaceId)",
        nativeQuery = true
    )
    void deleteAllByPracticeWorkspaceId(@Param("workspaceId") Long workspaceId);

    /**
     * Hard-delete the {@code CONVERSATION_THREAD} observations for a workspace whose {@code artifact_id} (the
     * {@code slack_thread} id) is one of {@code artifactIds} — the derived-content erasure the Slack module invokes
     * through {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure} when a channel's consent is
     * withdrawn. Workspace is scoped through the {@code Practice.workspace} relationship (this repo is
     * {@code @WorkspaceAgnostic}); the {@code artifactType} + {@code artifactId} predicates keep PR/ISSUE observations
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
        WHERE o.artifactType = de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.CONVERSATION_THREAD
          AND o.artifactId IN :artifactIds
          AND o.practice.id IN (SELECT p.id FROM Practice p WHERE p.workspace.id = :workspaceId)
        """
    )
    int deleteConversationThreadObservations(
        @Param("workspaceId") Long workspaceId,
        @Param("artifactIds") Collection<Long> artifactIds
    );

    /**
     * Hard-delete <em>every</em> {@code CONVERSATION_THREAD} observation for a workspace — the whole-tenant erasure
     * the Slack module invokes through
     * {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure#eraseAllConversationForWorkspace} on
     * app-uninstall / workspace-purge. Workspace is scoped through the {@code Practice.workspace} relationship (this
     * repo is {@code @WorkspaceAgnostic}); the {@code artifactType} predicate keeps PR/ISSUE observations and other
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
        WHERE o.artifactType = de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.CONVERSATION_THREAD
          AND o.practice.id IN (SELECT p.id FROM Practice p WHERE p.workspace.id = :workspaceId)
        """
    )
    int deleteAllConversationThreadObservations(@Param("workspaceId") Long workspaceId);

    /**
     * Hard-delete the {@code CONVERSATION_THREAD} observations a single person is the <em>subject</em> of
     * ({@code about_user_id = :aboutUserId}) within a workspace — the derived-content half of a person opt-out /
     * account hard-delete, invoked through
     * {@link de.tum.cit.aet.hephaestus.practices.spi.ConversationFeedbackErasure#eraseConversationFeedbackAboutUser}.
     * Workspace is scoped through {@code Practice.workspace}; the {@code artifactType} + {@code aboutUserId}
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
        WHERE o.artifactType = de.tum.cit.aet.hephaestus.practices.model.WorkArtifact.CONVERSATION_THREAD
          AND o.aboutUserId = :aboutUserId
          AND o.practice.id IN (SELECT p.id FROM Practice p WHERE p.workspace.id = :workspaceId)
        """
    )
    int deleteConversationThreadObservationsAboutUser(
        @Param("workspaceId") Long workspaceId,
        @Param("aboutUserId") Long aboutUserId
    );

    // Read queries for the developer dashboard (Issue #896)

    /**
     * Paginated findings for an about-user within a workspace, with optional filters.
     *
     * <p>Workspace scoping is done via the {@code Practice.workspace} join. The about-user is the
     * {@code about_user_id} subject the finding is filed against (ADR 0022).
     * Uses a separate {@code countQuery} because {@code JOIN FETCH} is incompatible
     * with count projections in Hibernate.
     */
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
     * Single finding by ID within a workspace, restricted to a specific about-user.
     *
     * <p>Ownership is enforced in the query (not in Java) to avoid lazy-load
     * fragility and to keep the auth check atomic with the fetch.
     */
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
    @Query(
        """
        SELECT f FROM Observation f
        JOIN FETCH f.practice p
        WHERE f.artifactType = :artifactType
        AND f.artifactId = :pullRequestId
        AND p.workspace.id = :workspaceId
        ORDER BY f.observedAt DESC
        """
    )
    List<Observation> findByPullRequestAndWorkspace(
        @Param("artifactType") WorkArtifact artifactType,
        @Param("pullRequestId") Long pullRequestId,
        @Param("workspaceId") Long workspaceId
    );

    // Aggregation for agent context (Issue #895)

    /**
     * Returns aggregated counts per (practice, presence, assessment) for a developer within a workspace.
     *
     * <p>Each row is one (practice slug, presence, assessment) combination with the total count and the
     * most recent detection timestamp. Callers group results by slug to build a per-practice
     * history summary. The {@code idx_observation_subject} index on {@code (about_user_id, observed_at DESC)}
     * narrows the initial scan by about-user.
     *
     * <p>Re-review deduped to each target's latest run (same grain as the sibling histogram queries), so a
     * twice-reviewed target contributes only its current state rather than inflating the good/bad counts that
     * drive the contributor-history ranking.
     *
     * @param aboutUserId the about-user whose history to aggregate
     * @param workspaceId   the workspace scope (via practice → workspace relationship)
     * @return aggregated summary rows ordered by slug then presence, empty if no findings exist
     */
    @Query(
        value = """
        SELECT p.slug AS practiceSlug,
               f.presence AS presence,
               f.assessment AS assessment,
               COUNT(f.id) AS count,
               MAX(f.observed_at) AS lastObservedAt
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
              WHERE f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
                AND target_artifact.id = f.artifact_id
          )
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                AND f2.about_user_id = f.about_user_id
              ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
          )
        GROUP BY p.slug, f.presence, f.assessment
        ORDER BY p.slug, f.presence
        """,
        nativeQuery = true
    )
    List<DeveloperPracticeSummary> findDeveloperPracticeSummary(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * A developer's recent observations, latest-run-deduped. Feeds two consumers: the mentor's
     * conversation context (referred to by title) and the developer's own reflection report cards
     * ({@code ObservationService#getPracticeReport}), which group and re-order these into per-practice cards.
     *
     * <p>Re-review deduped (same grain as {@link #findDeveloperPracticeSummary}): keeps only each
     * target's LATEST detection run, so a re-pushed PR's observations don't repeat across the list and a
     * consumer doesn't see (and re-litigate) the same observation four times. Native because the latest-run
     * selection needs {@code ORDER BY ... LIMIT 1} in a correlated subquery. The practice is loaded lazily
     * per observation (bounded by the page size) rather than JOIN-fetched.
     *
     * <p>{@code NOT_APPLICABLE} is excluded: it dominates the list (the bulk are "no change needed /
     * awaiting review" rows) neither consumer can act on, and would bury the actionable {@code BAD} problems
     * and {@code GOOD} strengths within the page budget. The NA total still reaches the mentor via the
     * presence-count summary. Rows come back recency-ordered; each consumer applies its own final ordering.
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
              WHERE f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
                AND target_artifact.id = f.artifact_id
          )
          AND f.observed_at >= :since
          AND f.presence <> 'NOT_APPLICABLE'
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                AND f2.about_user_id = f.about_user_id
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
              WHERE f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
                AND target_artifact.id = f.artifact_id
          )
          AND f.observed_at >= :since
          AND f.severity IS NOT NULL
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                AND f2.about_user_id = f.about_user_id
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
     * <p>Re-review deduped to each target's latest run (see {@link #findRecentByDeveloperAndWorkspace}).
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
              WHERE f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
                AND target_artifact.id = f.artifact_id
          )
          AND f.observed_at >= :since
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                AND f2.about_user_id = f.about_user_id
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
     */
    @Query(
        """
        SELECT f.agentJobId AS agentJobId, MAX(f.observedAt) AS runAt
        FROM Observation f JOIN f.practice p
        WHERE f.artifactType = :artifactType AND f.artifactId = :artifactId AND p.workspace.id = :workspaceId
          AND f.recurrenceKey IS NOT NULL
        GROUP BY f.agentJobId
        ORDER BY MAX(f.observedAt) DESC, f.agentJobId DESC
        """
    )
    List<RunRef> findRecentRunRefsForTarget(
        @Param("artifactType") WorkArtifact artifactType,
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
          AND f.presence <> de.tum.cit.aet.hephaestus.practices.model.Presence.NOT_APPLICABLE
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

    /**
     * Per-area standing rows for the mentor prepared context: one row per
     * (area, presence, assessment, severity) for a developer in the look-back window, with the
     * recent-window sub-count and the most-recent detection. The sign decision (problem vs strength)
     * is the per-observation {@code assessment} (ADR 0022): {@code BAD} is a problem, {@code GOOD} a strength.
     * Ungrouped practices ({@code p.area IS NULL}) are excluded; they remain visible in
     * {@code findings_history.json}.
     */
    @Query(
        value = """
        SELECT pa.slug AS "areaSlug", pa.name AS "areaName",
               f.presence AS "presence", f.assessment AS "assessment", f.severity AS "severity", COUNT(f.id) AS "count",
               SUM(CASE WHEN f.observed_at >= :recentSince THEN 1 ELSE 0 END) AS "recentCount",
               COUNT(DISTINCT f.artifact_id) AS "distinctTargets",
               MAX(f.confidence) AS "maxConfidence"
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        JOIN practice_area pa ON pa.id = p.practice_area_id
        WHERE f.about_user_id = :aboutUserId
          AND p.workspace_id = :workspaceId
          AND NOT EXISTS (
              SELECT 1
              FROM issue target_artifact
              JOIN workspace_team_repository_settings wtrs
                ON wtrs.workspace_id = p.workspace_id
               AND wtrs.repository_id = target_artifact.repository_id
               AND wtrs.hidden_from_contributions = true
              WHERE f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
                AND target_artifact.id = f.artifact_id
          )
          AND f.observed_at >= :since
          AND p.practice_area_id IS NOT NULL
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              JOIN practice p2 ON p2.id = f2.practice_id
              WHERE p2.workspace_id = p.workspace_id
                AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                AND f2.about_user_id = f.about_user_id
              ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
          )
        GROUP BY pa.slug, pa.name, f.presence, f.assessment, f.severity
        """,
        nativeQuery = true
    )
    List<AreaStandingRow> findAreaStandingByDeveloperAndWorkspace(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("recentSince") Instant recentSince
    );

    /**
     * Per-(developer, area, practice) good/bad activity for the mentor overview, across ALL practice areas
     * (P1 generalisation — the mentor roster/workspace health used to be hardcoded to one area). Keeps parity with the
     * reflection surface by deduping re-reviews to the target's latest run WITHIN the window (bounded by
     * {@code until} on both the outer predicate and the correlated latest-run subquery, so a prior-window call
     * never picks up a "latest run" that actually happened after that window closed) and excluding
     * low-confidence uncorroborated BAD observations from {@code badCount}. Rows stay at PRACTICE grain (one
     * row per developer × practice, carrying its area's slug/name/display-order) — callers roll practice rows
     * up to a per-area standing; the quarantine CTEs stay keyed on {@code practice_id} exactly as before.
     */
    @Query(
        value = """
        WITH filtered AS (
            SELECT f.about_user_id AS about_user_id,
                   u.login AS login,
                   u.name AS name,
                   u.avatar_url AS avatar_url,
                   p.id AS practice_id,
                   p.slug AS slug,
                   pa.slug AS area_slug,
                   pa.name AS area_name,
                   pa.display_order AS area_display_order,
                   f.assessment AS assessment,
                   f.confidence AS confidence,
                   f.artifact_id AS artifact_id,
                   f.recurrence_key AS recurrence_key
            FROM observation f
            JOIN practice p ON p.id = f.practice_id
            JOIN practice_area pa ON pa.id = p.practice_area_id
            JOIN "user" u ON u.id = f.about_user_id
            JOIN workspace_membership wm
              ON wm.workspace_id = p.workspace_id
             AND wm.user_id = f.about_user_id
             AND wm.hidden = false
            LEFT JOIN issue target_issue
              ON f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
             AND target_issue.id = f.artifact_id
            WHERE p.workspace_id = :workspaceId
              AND p.is_active = true
              AND u.type = 'USER'
              AND NOT EXISTS (
                  SELECT 1
                  FROM workspace_team_repository_settings wtrs
                  WHERE wtrs.workspace_id = p.workspace_id
                    AND wtrs.repository_id = target_issue.repository_id
                    AND wtrs.hidden_from_contributions = true
              )
              AND f.observed_at >= :since
              AND f.observed_at < :until
              AND f.presence <> 'NOT_APPLICABLE'
              AND f.agent_job_id = (
                  SELECT f2.agent_job_id FROM observation f2
                  JOIN practice p2 ON p2.id = f2.practice_id
                  WHERE p2.workspace_id = p.workspace_id
                    AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                    AND f2.about_user_id = f.about_user_id
                    AND f2.observed_at < :until
                  ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
              )
        ),
        locus_targets AS (
            SELECT about_user_id, practice_id, recurrence_key, COUNT(DISTINCT artifact_id) AS n
            FROM filtered
            WHERE assessment = 'BAD' AND recurrence_key IS NOT NULL
            GROUP BY about_user_id, practice_id, recurrence_key
        ),
        group_targets AS (
            SELECT about_user_id, practice_id, COUNT(DISTINCT artifact_id) AS n
            FROM filtered
            WHERE assessment = 'BAD'
            GROUP BY about_user_id, practice_id
        )
        SELECT f.about_user_id AS "aboutUserId",
               f.login AS "userLogin",
               f.name AS "userName",
               f.avatar_url AS "avatarUrl",
               f.area_slug AS "areaSlug",
               f.area_name AS "areaName",
               f.area_display_order AS "areaDisplayOrder",
               f.slug AS "practiceSlug",
               SUM(CASE WHEN f.assessment = 'GOOD' THEN 1 ELSE 0 END) AS "goodCount",
               SUM(CASE WHEN f.assessment = 'BAD' AND NOT (
                       COALESCE(f.confidence, 0) < 0.5
                       AND (CASE WHEN f.recurrence_key IS NOT NULL
                                 THEN COALESCE(lt.n, 0) ELSE COALESCE(gt.n, 0) END) < 2
                   ) THEN 1 ELSE 0 END) AS "badCount"
        FROM filtered f
        LEFT JOIN locus_targets lt
               ON lt.about_user_id = f.about_user_id AND lt.practice_id = f.practice_id
              AND lt.recurrence_key = f.recurrence_key
        LEFT JOIN group_targets gt
               ON gt.about_user_id = f.about_user_id AND gt.practice_id = f.practice_id
        GROUP BY f.about_user_id, f.login, f.name, f.avatar_url, f.area_slug, f.area_name, f.area_display_order, f.slug
        ORDER BY f.login ASC, f.area_display_order ASC, f.slug ASC
        """,
        nativeQuery = true
    )
    List<AreaRollupRow> findAreaRollupStandingBetween(
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    @Query(
        value = """
        SELECT EXISTS (
            SELECT 1
            FROM observation f
            JOIN practice p ON p.id = f.practice_id
            JOIN practice_area pa ON pa.id = p.practice_area_id
            JOIN "user" u ON u.id = f.about_user_id
            JOIN workspace_membership wm
              ON wm.workspace_id = p.workspace_id
             AND wm.user_id = f.about_user_id
             AND wm.hidden = false
            LEFT JOIN issue target_issue
              ON f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
             AND target_issue.id = f.artifact_id
            WHERE p.workspace_id = :workspaceId
              AND f.about_user_id = :aboutUserId
              AND p.is_active = true
              AND u.type = 'USER'
              AND NOT EXISTS (
                  SELECT 1
                  FROM workspace_team_repository_settings wtrs
                  WHERE wtrs.workspace_id = p.workspace_id
                    AND wtrs.repository_id = target_issue.repository_id
                    AND wtrs.hidden_from_contributions = true
              )
              AND f.observed_at >= :since
              AND f.observed_at < :until
              AND f.presence <> 'NOT_APPLICABLE'
              AND f.agent_job_id = (
                  SELECT f2.agent_job_id FROM observation f2
                  JOIN practice p2 ON p2.id = f2.practice_id
                  WHERE p2.workspace_id = p.workspace_id
                    AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                    AND f2.about_user_id = f.about_user_id
                    AND f2.observed_at < :until
                  ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
              )
        )
        """,
        nativeQuery = true
    )
    boolean existsVisibleReportSubjectBetween(
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("until") Instant until,
        @Param("aboutUserId") Long aboutUserId
    );

    /**
     * Per-practice good/bad activity for ONE developer in a bounded window — the prior-window standing input
     * for the reflection/drill-down cards' cycle-over-cycle {@code trend}. Same grain and quarantine floor as
     * {@link #findRecentByDeveloperAndWorkspace} (hidden-repo excluded, {@code NOT_APPLICABLE} excluded,
     * latest-run-within-window deduped), aggregated to (practice, good/bad counts) instead of individual
     * observations, and NOT restricted to area-bound practices (a card can exist for an ungrouped practice).
     */
    @Query(
        value = """
        WITH filtered AS (
            SELECT p.id AS practice_id,
                   p.slug AS slug,
                   f.assessment AS assessment,
                   f.confidence AS confidence,
                   f.artifact_id AS artifact_id,
                   f.recurrence_key AS recurrence_key
            FROM observation f
            JOIN practice p ON p.id = f.practice_id
            LEFT JOIN issue target_issue
              ON f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
             AND target_issue.id = f.artifact_id
            WHERE f.about_user_id = :aboutUserId
              AND p.workspace_id = :workspaceId
              AND NOT EXISTS (
                  SELECT 1
                  FROM workspace_team_repository_settings wtrs
                  WHERE wtrs.workspace_id = p.workspace_id
                    AND wtrs.repository_id = target_issue.repository_id
                    AND wtrs.hidden_from_contributions = true
              )
              AND f.observed_at >= :since
              AND f.observed_at < :until
              AND f.presence <> 'NOT_APPLICABLE'
              AND f.agent_job_id = (
                  SELECT f2.agent_job_id FROM observation f2
                  JOIN practice p2 ON p2.id = f2.practice_id
                  WHERE p2.workspace_id = p.workspace_id
                    AND f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
                    AND f2.about_user_id = f.about_user_id
                    AND f2.observed_at < :until
                  ORDER BY f2.observed_at DESC, f2.agent_job_id DESC LIMIT 1
              )
        ),
        locus_targets AS (
            SELECT practice_id, recurrence_key, COUNT(DISTINCT artifact_id) AS n
            FROM filtered
            WHERE assessment = 'BAD' AND recurrence_key IS NOT NULL
            GROUP BY practice_id, recurrence_key
        ),
        group_targets AS (
            SELECT practice_id, COUNT(DISTINCT artifact_id) AS n
            FROM filtered
            WHERE assessment = 'BAD'
            GROUP BY practice_id
        )
        SELECT f.slug AS "practiceSlug",
               SUM(CASE WHEN f.assessment = 'GOOD' THEN 1 ELSE 0 END) AS "goodCount",
               SUM(CASE WHEN f.assessment = 'BAD' AND NOT (
                       COALESCE(f.confidence, 0) < 0.5
                       AND (CASE WHEN f.recurrence_key IS NOT NULL
                                 THEN COALESCE(lt.n, 0) ELSE COALESCE(gt.n, 0) END) < 2
                   ) THEN 1 ELSE 0 END) AS "badCount"
        FROM filtered f
        LEFT JOIN locus_targets lt
               ON lt.practice_id = f.practice_id AND lt.recurrence_key = f.recurrence_key
        LEFT JOIN group_targets gt
               ON gt.practice_id = f.practice_id
        GROUP BY f.slug
        """,
        nativeQuery = true
    )
    List<PracticeStandingRow> findPracticeStandingForDeveloperBetween(
        @Param("aboutUserId") Long aboutUserId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /**
     * Projection: one developer's good/bad activity on one practice, WITH its area — the standing input the
     * mentor overview rolls up to an area-grain {@link de.tum.cit.aet.hephaestus.practices.observation.PracticeStatus}.
     */
    interface AreaRollupRow {
        Long getAboutUserId();
        String getUserLogin();
        String getUserName();
        String getAvatarUrl();
        String getAreaSlug();
        String getAreaName();
        Integer getAreaDisplayOrder();
        String getPracticeSlug();
        Long getGoodCount();
        Long getBadCount();
    }

    /** Projection: one developer's good/bad activity on one practice in a bounded window (trend input). */
    interface PracticeStandingRow {
        String getPracticeSlug();
        Long getGoodCount();
        Long getBadCount();
    }

    /** Projection: per (area, presence, assessment, severity) standing for a developer. */
    interface AreaStandingRow {
        String getAreaSlug();
        String getAreaName();
        Presence getPresence();
        Assessment getAssessment();
        Severity getSeverity();
        Long getCount();
        Long getRecentCount();

        /**
         * Distinct review targets (artifact_id) contributing to this row — the corroboration signal the
         * standing floor keys on. One BAD on one PR is {@code 1}; the same gap on two distinct PRs is
         * {@code 2}. A single-target gap must not set MAJOR area-priority on its own (see
         * {@code PracticeStandingContentSource}).
         */
        Long getDistinctTargets();

        /**
         * The strongest (highest) confidence in this row's group — the quarantine signal. A very
         * low-confidence BAD must not become a headline priority on the detector's hunch alone.
         */
        Float getMaxConfidence();
    }
}
