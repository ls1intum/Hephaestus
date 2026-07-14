package de.tum.cit.aet.hephaestus.practices.observation;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.model.Assessment;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.WorkArtifact;
import de.tum.cit.aet.hephaestus.practices.observation.dto.DeveloperPracticeSummaryProjection;
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
     * Per-practice aggregation for the developer dashboard: present/good and bad counts, and last finding date.
     *
     * <p><b>Re-review dedup (ADR 0021):</b> a target gets re-detected on every push, and every run writes a
     * fresh {@link Observation} row — so a naive {@code COUNT} over all rows inflates the dashboard by the
     * re-review multiplier (a target re-reviewed N times shows N× the findings). The dashboard reflects each
     * target's CURRENT state, so this query keeps only the findings from each target's LATEST detection run
     * ({@code agent_job_id} with the most recent {@code observed_at} for that
     * {@code (artifact_type, artifact_id)}, tiebroken by {@code agent_job_id} so two runs sharing a timestamp
     * still select ONE run deterministically). Superseded runs do not count toward the habit signal.
     *
     * <p><b>Hidden repositories:</b> observations on artifacts in a repository that ANY team's settings mark
     * {@code hidden_from_contributions} are excluded. Unlike the team-scoped activity/leaderboard queries,
     * these endpoints carry no viewing-team context, so the exclusion fails closed: hidden for one team means
     * hidden here for everyone. This applies to the aggregate serving queries (this one and the five siblings
     * that reference it); the raw per-artifact fetches ({@link #findByAboutUserAndWorkspace},
     * {@link #findByPullRequestAndWorkspace}) are unfiltered.
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
              WHERE f.artifact_type IN ('PULL_REQUEST', 'ISSUE')
                AND target_artifact.id = f.artifact_id
          )
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              WHERE f2.artifact_type = f.artifact_type
                AND f2.artifact_id = f.artifact_id
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
     * history summary. The {@code idx_observation_subject} index on {@code (about_user_id)} narrows the
     * initial scan by about-user.
     *
     * <p>Re-review deduped to each target's latest run (same grain as the sibling histogram queries), so a
     * twice-reviewed target contributes only its current state rather than inflating the good/bad counts that
     * drive the contributor-history ranking. Observations on artifacts in hidden repositories are excluded
     * (see {@link #findSummaryByDeveloperAndWorkspace}).
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
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
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
     * Recent findings the mentor can refer to by title in conversation.
     *
     * <p>Re-review deduped (same grain as {@link #findSummaryByDeveloperAndWorkspace}): keeps only each
     * target's LATEST detection run, so a re-pushed PR's findings don't repeat across the list and the
     * mentor doesn't see (and re-litigate) the same finding four times. Native because the latest-run
     * selection needs {@code ORDER BY ... LIMIT 1} in a correlated subquery. The practice is loaded lazily
     * per finding (bounded by the page size) rather than JOIN-fetched.
     *
     * <p>{@code NOT_APPLICABLE} is excluded: it dominates the list (the bulk are "no change needed /
     * awaiting review" rows) the mentor cannot coach from, and would bury the actionable {@code BAD} problems
     * and {@code GOOD} strengths within the page budget. The NA total still reaches the mentor via the
     * presence-count summary; this is the drill-down list only, and stays recency-ordered (NOT re-ordered by
     * severity) to preserve its "what happened lately" purpose. Observations on artifacts in hidden
     * repositories are excluded (see {@link #findSummaryByDeveloperAndWorkspace}).
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
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
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
     * {@code BAD} findings carry a non-null severity, so the histogram is over problems. Observations on
     * artifacts in hidden repositories are excluded (see {@link #findSummaryByDeveloperAndWorkspace}).
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
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
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
     * Observations on artifacts in hidden repositories are excluded (see
     * {@link #findSummaryByDeveloperAndWorkspace}).
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
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
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
     * {@code findings_history.json}. Observations on artifacts in hidden repositories are excluded (see
     * {@link #findSummaryByDeveloperAndWorkspace}).
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
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
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
         * standing floor keys on (P4). One BAD on one PR is {@code 1}; the same gap on two distinct PRs is
         * {@code 2}. A single-target gap must not set MAJOR area-priority on its own (see
         * {@code PracticeStandingContentSource}).
         */
        Long getDistinctTargets();

        /**
         * The strongest (highest) confidence in this row's group — the quarantine signal (P4). A very
         * low-confidence BAD must not become a headline priority on the detector's hunch alone.
         */
        Float getMaxConfidence();
    }
}
