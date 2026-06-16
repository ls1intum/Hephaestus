package de.tum.cit.aet.hephaestus.practices.finding;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.finding.dto.DeveloperPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.model.Observation;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
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
public interface PracticeFindingRepository extends JpaRepository<PracticeFinding, UUID> {
    /**
     * Finds a practice finding by ID, scoped to a specific workspace.
     * Used to validate that a finding belongs to the caller's workspace before allowing operations on it.
     */
    @Query("SELECT f FROM PracticeFinding f JOIN f.practice p WHERE f.id = :id AND p.workspace.id = :workspaceId")
    Optional<PracticeFinding> findByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") Long workspaceId);

    /** All findings a given agent job produced — the source set the feedback ledger recorder binds to. */
    List<PracticeFinding> findByAgentJobId(UUID agentJobId);

    /**
     * Atomically inserts a practice finding if absent (race-condition safe).
     *
     * <p>Uses PostgreSQL's ON CONFLICT DO NOTHING to handle concurrent inserts.
     * This avoids the race condition where exists() check passes but save() fails
     * with DataIntegrityViolationException at transaction commit time.
     *
     * @return 1 if inserted, 0 if duplicate (conflict on idempotency_key)
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO practice_finding (
            id, idempotency_key, agent_job_id, practice_id,
            artifact_type, artifact_id, developer_id, subject_user_id,
            title, verdict, severity, confidence,
            evidence, reasoning, guidance,
            finding_fingerprint, detected_at
        )
        VALUES (
            :id, :idempotencyKey, :agentJobId, :practiceId,
            :artifactType, :artifactId, :developerId, :subjectUserId,
            :title, :verdict, :severity, :confidence,
            CAST(:evidence AS jsonb), :reasoning, :guidance,
            :findingFingerprint, :detectedAt
        )
        ON CONFLICT (idempotency_key) DO NOTHING
        """,
        nativeQuery = true
    )
    int insertIfAbsent(
        @Param("id") UUID id,
        @Param("idempotencyKey") String idempotencyKey,
        @Param("agentJobId") UUID agentJobId,
        @Param("practiceId") Long practiceId,
        @Param("artifactType") String artifactType,
        @Param("artifactId") Long artifactId,
        @Param("developerId") Long developerId,
        @Param("subjectUserId") Long subjectUserId,
        @Param("title") String title,
        @Param("verdict") String verdict,
        @Param("severity") String severity,
        @Param("confidence") Float confidence,
        @Param("evidence") String evidence,
        @Param("reasoning") String reasoning,
        @Param("guidance") String guidance,
        @Param("findingFingerprint") String findingFingerprint,
        @Param("detectedAt") Instant detectedAt
    );

    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM practice_finding WHERE practice_id IN (SELECT id FROM practice WHERE workspace_id = :workspaceId)",
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
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.developer.id = :developerId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:verdict IS NULL OR f.verdict = :verdict)
        """,
        countQuery = """
        SELECT COUNT(f) FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.developer.id = :developerId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:verdict IS NULL OR f.verdict = :verdict)
        """
    )
    Page<PracticeFinding> findByDeveloperAndWorkspace(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("practiceSlug") String practiceSlug,
        @Param("verdict") Observation verdict,
        Pageable pageable
    );

    /**
     * Per-practice aggregation: verdict counts and last finding date for a developer.
     */
    @Query(
        """
        SELECT p.slug AS practiceSlug,
               p.name AS practiceName,
               COUNT(f) AS totalFindings,
               SUM(CASE WHEN f.verdict = de.tum.cit.aet.hephaestus.practices.model.Observation.OBSERVED THEN 1L ELSE 0L END) AS observedCount,
               SUM(CASE WHEN f.verdict = de.tum.cit.aet.hephaestus.practices.model.Observation.NOT_OBSERVED THEN 1L ELSE 0L END) AS notObservedCount,
               MAX(f.detectedAt) AS lastFindingAt
        FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.developer.id = :developerId
        AND p.workspace.id = :workspaceId
        GROUP BY p.slug, p.name
        ORDER BY p.name ASC
        """
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
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.id = :findingId
        AND f.developer.id = :developerId
        AND p.workspace.id = :workspaceId
        """
    )
    Optional<PracticeFinding> findByIdAndDeveloperAndWorkspace(
        @Param("findingId") UUID findingId,
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * All findings for a specific pull request within a workspace.
     */
    @Query(
        """
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.artifactType = :artifactType
        AND f.artifactId = :pullRequestId
        AND p.workspace.id = :workspaceId
        ORDER BY f.detectedAt DESC
        """
    )
    List<PracticeFinding> findByPullRequestAndWorkspace(
        @Param("artifactType") WorkArtifact artifactType,
        @Param("pullRequestId") Long pullRequestId,
        @Param("workspaceId") Long workspaceId
    );

    // Aggregation for agent context (Issue #895)

    /**
     * Returns aggregated verdict counts per practice for a developer within a workspace.
     *
     * <p>Each row is one (practice slug, verdict) combination with the total count and the
     * most recent detection timestamp. Callers group results by slug to build a per-practice
     * history summary. The {@code idx_practice_finding_developer_detected} index on
     * {@code (developer_id, detected_at DESC)} narrows the initial scan by developer.
     *
     * @param developerId the developer whose history to aggregate
     * @param workspaceId   the workspace scope (via practice → workspace relationship)
     * @return aggregated summary rows ordered by slug then verdict, empty if no findings exist
     */
    @Query(
        """
        SELECT pf.practice.slug AS practiceSlug,
               pf.verdict AS verdict,
               COUNT(pf) AS count,
               MAX(pf.detectedAt) AS lastDetectedAt
        FROM PracticeFinding pf
        WHERE pf.developer.id = :developerId
          AND pf.practice.workspace.id = :workspaceId
        GROUP BY pf.practice.slug, pf.verdict
        ORDER BY pf.practice.slug, pf.verdict
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
    @Query(
        """
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.developer.id = :developerId
          AND p.workspace.id = :workspaceId
          AND f.detectedAt >= :since
        ORDER BY f.detectedAt DESC
        """
    )
    List<PracticeFinding> findRecentByDeveloperAndWorkspace(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Severity histogram for a developer's findings within a workspace.
     * Returns {@code [severityName, count]} rows — caller maps to a name→count map.
     */
    @Query(
        """
        SELECT f.severity AS severity, COUNT(f) AS count
        FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.developer.id = :developerId
          AND p.workspace.id = :workspaceId
          AND f.detectedAt >= :since
        GROUP BY f.severity
        """
    )
    List<SeverityCount> countBySeverityForDeveloper(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since
    );

    /** Observation histogram for a developer's findings within a workspace. */
    @Query(
        """
        SELECT f.verdict AS verdict, COUNT(f) AS count
        FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.developer.id = :developerId
          AND p.workspace.id = :workspaceId
          AND f.detectedAt >= :since
        GROUP BY f.verdict
        """
    )
    List<VerdictCount> countByVerdictForDeveloper(
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
        FROM PracticeFinding f JOIN f.practice p
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
        SELECT f.agentJobId AS agentJobId, f.findingFingerprint AS findingFingerprint, f.verdict AS verdict,
               f.severity AS severity, f.confidence AS confidence, p.slug AS practiceSlug,
               f.title AS title, f.detectedAt AS detectedAt
        FROM PracticeFinding f JOIN f.practice p
        WHERE f.agentJobId IN :agentJobIds AND p.workspace.id = :workspaceId AND f.findingFingerprint IS NOT NULL
          AND f.verdict <> de.tum.cit.aet.hephaestus.practices.model.Observation.NOT_APPLICABLE
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
        String getFindingFingerprint();
        Observation getVerdict();
        Severity getSeverity();
        Float getConfidence();
        String getPracticeSlug();
        String getTitle();
        Instant getDetectedAt();
    }

    /** Projection: severity → count. */
    interface SeverityCount {
        de.tum.cit.aet.hephaestus.practices.model.Severity getSeverity();
        Long getCount();
    }

    /** Projection: verdict → count. */
    interface VerdictCount {
        de.tum.cit.aet.hephaestus.practices.model.Observation getVerdict();
        Long getCount();
    }

    /**
     * Per-goal standing rows for the mentor prepared-context aspect: one row per
     * (goal, polarity, verdict, severity) for a developer in the look-back window, with the
     * recent-window sub-count and the most-recent detection. The sign decision (problem vs strength)
     * is deliberately left to {@link de.tum.cit.aet.hephaestus.practices.model.Polarity} in Java —
     * this query only projects the raw verdict + polarity so the rule stays single-sourced.
     * Ungrouped practices ({@code p.goal IS NULL}) are excluded; they remain visible in
     * {@code findings_history.json}.
     */
    @Query(
        """
        SELECT p.goal.slug AS goalSlug, p.goal.name AS goalName, p.polarity AS polarity,
               f.verdict AS verdict, f.severity AS severity, COUNT(f) AS count,
               SUM(CASE WHEN f.detectedAt >= :recentSince THEN 1L ELSE 0L END) AS recentCount
        FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.developer.id = :developerId
          AND p.workspace.id = :workspaceId
          AND f.detectedAt >= :since
          AND p.goal IS NOT NULL
        GROUP BY p.goal.slug, p.goal.name, p.polarity, f.verdict, f.severity
        """
    )
    List<GoalStandingRow> findGoalStandingByDeveloperAndWorkspace(
        @Param("developerId") Long developerId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        @Param("recentSince") Instant recentSince
    );

    /** Projection: per (goal, polarity, verdict, severity) standing for a developer. */
    interface GoalStandingRow {
        String getGoalSlug();
        String getGoalName();
        de.tum.cit.aet.hephaestus.practices.model.Polarity getPolarity();
        de.tum.cit.aet.hephaestus.practices.model.Observation getVerdict();
        de.tum.cit.aet.hephaestus.practices.model.Severity getSeverity();
        Long getCount();
        Long getRecentCount();
    }
}
