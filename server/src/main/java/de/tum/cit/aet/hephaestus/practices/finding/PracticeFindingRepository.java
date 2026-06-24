package de.tum.cit.aet.hephaestus.practices.finding;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.finding.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.model.Presence;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
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
public interface PracticeFindingRepository extends JpaRepository<Observation, UUID> {
    /**
     * Finds a practice finding by ID, scoped to a specific workspace.
     * Used to validate that a finding belongs to the caller's workspace before allowing operations on it.
     */
    @Query("SELECT f FROM Observation f JOIN f.practice p WHERE f.id = :id AND p.workspace.id = :workspaceId")
    Optional<Observation> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") Long workspaceId);

    /** All findings a given agent job produced — the source set the feedback ledger recorder binds to. */
    List<Observation> findByAgentJobId(UUID agentJobId);

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
            artifact_type, artifact_id, developer_id, about_user_id,
            title, observation, severity, confidence,
            evidence, reasoning,
            recurrence_key, observed_at
        )
        VALUES (
            :id, :idempotencyKey, :agentJobId, :practiceId, :practiceRevisionId,
            :artifactType, :artifactId, :developerId, :subjectUserId,
            :title, :observation, :severity, :confidence,
            CAST(:evidence AS jsonb), :reasoning,
            :findingFingerprint, :detectedAt
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
        @Param("developerId") Long developerId,
        @Param("subjectUserId") Long subjectUserId,
        @Param("title") String title,
        @Param("observation") String observation,
        @Param("severity") String severity,
        @Param("confidence") Float confidence,
        @Param("evidence") String evidence,
        @Param("reasoning") String reasoning,
        @Param("findingFingerprint") String findingFingerprint,
        @Param("detectedAt") Instant detectedAt
    );

    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM observation WHERE practice_id IN (SELECT id FROM practice WHERE workspace_id = :workspaceId)",
        nativeQuery = true
    )
    void deleteAllByPracticeWorkspaceId(@Param("workspaceId") Long workspaceId);

    // Read queries for the developer dashboard (Issue #896)

    /**
     * Paginated findings for a developer within a workspace, with optional filters.
     *
     * <p>Workspace scoping is done via the {@code Practice.workspace} join.
     * Uses a separate {@code countQuery} because {@code JOIN FETCH} is incompatible
     * with count projections in Hibernate.
     */
    @Query(
        value = """
        SELECT f FROM Observation f
        JOIN FETCH f.practice p
        WHERE f.developer.id = :developerId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:observation IS NULL OR f.observation = :observation)
        """,
        countQuery = """
        SELECT COUNT(f) FROM Observation f
        JOIN f.practice p
        WHERE f.developer.id = :developerId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:observation IS NULL OR f.observation = :observation)
        """
    )
    Page<Observation> findByDeveloperAndWorkspace(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("practiceSlug") String practiceSlug,
        @Param("observation") Presence observation,
        Pageable pageable
    );

    /**
     * Per-practice aggregation for the developer dashboard: observation counts and last finding date.
     *
     * <p><b>Re-review dedup (ADR 0021):</b> a target gets re-detected on every push, and every run writes a
     * fresh {@link Observation} row — so a naive {@code COUNT} over all rows inflates the dashboard by the
     * re-review multiplier (a target re-reviewed N times shows N× the findings). The dashboard must reflect each target's CURRENT state, so this query keeps only the findings
     * from each target's LATEST detection run ({@code agent_job_id} with the most recent {@code observed_at}
     * for that {@code (artifact_type, artifact_id)}). Superseded runs no longer count toward the habit signal.
     *
     * <p>Native (not JPQL) because the latest-run-per-target selection needs {@code ORDER BY ... LIMIT 1} in a
     * correlated subquery, which JPQL cannot express. Aliases are quoted so the JDBC column labels match the
     * {@link DeveloperPracticeSummaryProjection} getters exactly (Postgres folds unquoted identifiers to
     * lower-case). Enum columns compare against their {@code STRING} storage form.
     */
    @Query(
        value = """
        SELECT p.slug AS "practiceSlug",
               p.name AS "practiceName",
               COUNT(f.id) AS "totalFindings",
               SUM(CASE WHEN f.observation = 'OBSERVED' THEN 1 ELSE 0 END) AS "observedCount",
               SUM(CASE WHEN f.observation = 'NOT_OBSERVED' THEN 1 ELSE 0 END) AS "notObservedCount",
               MAX(f.observed_at) AS "lastFindingAt"
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.developer_id = :developerId
          AND p.workspace_id = :workspaceId
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              WHERE f2.artifact_type = f.artifact_type
                AND f2.artifact_id = f.artifact_id
              ORDER BY f2.observed_at DESC
              LIMIT 1
          )
        GROUP BY p.slug, p.name
        ORDER BY p.name ASC
        """,
        nativeQuery = true
    )
    List<DeveloperPracticeSummaryProjection> findSummaryByDeveloperAndWorkspace(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Single finding by ID within a workspace, restricted to a specific developer.
     *
     * <p>Ownership is enforced in the query (not in Java) to avoid lazy-load
     * fragility and to keep the auth check atomic with the fetch.
     */
    @Query(
        """
        SELECT f FROM Observation f
        JOIN FETCH f.practice p
        WHERE f.id = :findingId
        AND f.developer.id = :developerId
        AND p.workspace.id = :workspaceId
        """
    )
    Optional<Observation> findByIdAndDeveloperAndWorkspace(
        @Param("findingId") UUID findingId,
        @Param("developerId") Long developerId,
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
        ORDER BY f.detectedAt DESC
        """
    )
    List<Observation> findByPullRequestAndWorkspace(
        @Param("artifactType") WorkArtifact artifactType,
        @Param("pullRequestId") Long pullRequestId,
        @Param("workspaceId") Long workspaceId
    );

    // Aggregation for agent context (Issue #895)

    /**
     * Returns aggregated observation counts per practice for a developer within a workspace.
     *
     * <p>Each row is one (practice slug, observation) combination with the total count and the
     * most recent detection timestamp. Callers group results by slug to build a per-practice
     * history summary. The {@code idx_observation_developer_observed} index on
     * {@code (developer_id, observed_at DESC)} narrows the initial scan by developer.
     *
     * @param developerId the developer whose history to aggregate
     * @param workspaceId   the workspace scope (via practice → workspace relationship)
     * @return aggregated summary rows ordered by slug then observation, empty if no findings exist
     */
    @Query(
        """
        SELECT pf.practice.slug AS practiceSlug,
               pf.observation AS observation,
               COUNT(pf) AS count,
               MAX(pf.detectedAt) AS lastDetectedAt
        FROM Observation pf
        WHERE pf.developer.id = :developerId
          AND pf.practice.workspace.id = :workspaceId
        GROUP BY pf.practice.slug, pf.observation
        ORDER BY pf.practice.slug, pf.observation
        """
    )
    List<DeveloperPracticeSummary> findDeveloperPracticeSummary(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Recent findings for a developer within a workspace since a cutoff, newest first.
     *
     * <p>Used by the mentor's {@code findings_history.json} aspect: the agent needs to see
     * what specific findings have been delivered to the developer lately so it can refer
     * to them by title in the conversation. Bounded by {@code limit} at the caller so the
     * page size stays a JPA concern.
     */
    /**
     * Recent findings the mentor can refer to by title in conversation.
     *
     * <p>Re-review deduped (same grain as {@link #findSummaryByDeveloperAndWorkspace}): keeps only each
     * target's LATEST detection run, so a re-pushed PR's findings don't repeat across the list and the
     * mentor doesn't see (and re-litigate) the same finding four times. Native because the latest-run
     * selection needs {@code ORDER BY ... LIMIT 1} in a correlated subquery. The practice is loaded lazily
     * per finding (bounded by the page size) rather than JOIN-fetched.
     *
     * <p>{@code NOT_APPLICABLE} is excluded: it dominates the list (the bulk are "no change needed / awaiting review" rows) and spent the page budget on findings the mentor cannot
     * coach from, burying the actionable {@code NOT_OBSERVED} defects and {@code OBSERVED} strengths. The
     * NA total still reaches the mentor via the observation-count summary; this is the drill-down list only,
     * and stays recency-ordered (NOT re-ordered by severity) to preserve its "what happened lately" purpose.
     */
    @Query(
        value = """
        SELECT f.* FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.developer_id = :developerId
          AND p.workspace_id = :workspaceId
          AND f.observed_at >= :since
          AND f.observation <> 'NOT_APPLICABLE'
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
              ORDER BY f2.observed_at DESC LIMIT 1
          )
        ORDER BY f.observed_at DESC
        """,
        nativeQuery = true
    )
    List<Observation> findRecentByDeveloperAndWorkspace(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Severity histogram for a developer's findings within a workspace.
     * Returns {@code [severityName, count]} rows — caller maps to a name→count map.
     *
     * <p>Re-review deduped to each target's latest run (see {@link #findRecentByDeveloperAndWorkspace}) so
     * the mentor's "how am I doing" histogram reflects current state, not the re-push multiplier.
     */
    @Query(
        value = """
        SELECT f.severity AS severity, COUNT(f.id) AS count
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.developer_id = :developerId
          AND p.workspace_id = :workspaceId
          AND f.observed_at >= :since
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
              ORDER BY f2.observed_at DESC LIMIT 1
          )
        GROUP BY f.severity
        """,
        nativeQuery = true
    )
    List<SeverityCount> countBySeverityForDeveloper(
        @Param("developerId") Long developerId,
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
        SELECT f.observation AS observation, COUNT(f.id) AS count
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        WHERE f.developer_id = :developerId
          AND p.workspace_id = :workspaceId
          AND f.observed_at >= :since
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
              ORDER BY f2.observed_at DESC LIMIT 1
          )
        GROUP BY f.observation
        """,
        nativeQuery = true
    )
    List<ObservationCount> countByObservationForDeveloper(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since
    );

    // Cross-run trend read path (ADR 0021, A1) — the measurement substrate FindingTrendService classifies.

    /**
     * The runs (agent jobs) that produced ≥1 correlation-keyed finding for a target, newest first by the
     * run's latest detection. Pass {@code PageRequest.of(0, 2)} to get the two most-recent runs to diff.
     * Workspace-scoped via {@code Practice.workspace}.
     */
    @Query(
        """
        SELECT f.agentJobId AS agentJobId, MAX(f.detectedAt) AS runAt
        FROM Observation f JOIN f.practice p
        WHERE f.artifactType = :artifactType AND f.artifactId = :artifactId AND p.workspace.id = :workspaceId
          AND f.findingFingerprint IS NOT NULL
        GROUP BY f.agentJobId
        ORDER BY MAX(f.detectedAt) DESC, f.agentJobId DESC
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
        SELECT f.agentJobId AS agentJobId, f.findingFingerprint AS findingFingerprint, f.observation AS observation,
               f.severity AS severity, f.confidence AS confidence, p.slug AS practiceSlug,
               f.title AS title, f.detectedAt AS detectedAt
        FROM Observation f JOIN f.practice p
        WHERE f.agentJobId IN :agentJobIds AND p.workspace.id = :workspaceId AND f.findingFingerprint IS NOT NULL
          AND f.observation <> de.tum.cit.aet.hephaestus.practices.model.Presence.NOT_APPLICABLE
        ORDER BY f.detectedAt DESC
        """
    )
    List<LocusFinding> findLociByAgentJobs(
        @Param("agentJobIds") Collection<UUID> agentJobIds,
        @Param("workspaceId") Long workspaceId
    );

    /** Projection: one run (agent job) with its latest detection timestamp. */
    interface RunRef {
        UUID getAgentJobId();
        Instant getRunAt();
    }

    /** Projection: a correlation-keyed finding reduced to the fields the trend classifier needs. */
    interface LocusFinding {
        UUID getAgentJobId();
        String getRecurrenceKey();
        Presence getObservation();
        Severity getSeverity();
        Float getConfidence();
        String getPracticeSlug();
        String getTitle();
        Instant getObservedAt();
    }

    /** Projection: severity → count. */
    interface SeverityCount {
        de.tum.cit.aet.hephaestus.practices.model.Severity getSeverity();
        Long getCount();
    }

    /** Projection: observation → count. */
    interface ObservationCount {
        de.tum.cit.aet.hephaestus.practices.model.Presence getObservation();
        Long getCount();
    }

    /**
     * Per-area standing rows for the mentor prepared-context aspect: one row per
     * (area, kind, observation, severity) for a developer in the look-back window, with the
     * recent-window sub-count and the most-recent detection. The sign decision (problem vs strength)
     * is deliberately left to {@link de.tum.cit.aet.hephaestus.practices.model.PracticeKind} in Java —
     * this query only projects the raw observation + kind so the rule stays single-sourced.
     * Ungrouped practices ({@code p.area IS NULL}) are excluded; they remain visible in
     * {@code findings_history.json}.
     */
    @Query(
        value = """
        SELECT pa.slug AS "areaSlug", pa.name AS "areaName", p.kind AS "kind",
               f.observation AS "observation", f.severity AS "severity", COUNT(f.id) AS "count",
               SUM(CASE WHEN f.observed_at >= :recentSince THEN 1 ELSE 0 END) AS "recentCount"
        FROM observation f
        JOIN practice p ON p.id = f.practice_id
        JOIN practice_area pa ON pa.id = p.practice_area_id
        WHERE f.developer_id = :developerId
          AND p.workspace_id = :workspaceId
          AND f.observed_at >= :since
          AND p.practice_area_id IS NOT NULL
          AND f.agent_job_id = (
              SELECT f2.agent_job_id FROM observation f2
              WHERE f2.artifact_type = f.artifact_type AND f2.artifact_id = f.artifact_id
              ORDER BY f2.observed_at DESC LIMIT 1
          )
        GROUP BY pa.slug, pa.name, p.kind, f.observation, f.severity
        """,
        nativeQuery = true
    )
    List<AreaStandingRow> findAreaStandingByDeveloperAndWorkspace(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("recentSince") Instant recentSince
    );

    /** Projection: per (area, kind, observation, severity) standing for a developer. */
    interface AreaStandingRow {
        String getAreaSlug();
        String getAreaName();
        de.tum.cit.aet.hephaestus.practices.model.PracticeKind getKind();
        de.tum.cit.aet.hephaestus.practices.model.Presence getObservation();
        de.tum.cit.aet.hephaestus.practices.model.Severity getSeverity();
        Long getCount();
        Long getRecentCount();
    }
}
