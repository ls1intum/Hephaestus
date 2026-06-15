package de.tum.cit.aet.hephaestus.practices.finding;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.finding.dto.ContributorPracticeSummaryProjection;
import de.tum.cit.aet.hephaestus.practices.model.PracticeFinding;
import de.tum.cit.aet.hephaestus.practices.model.Severity;
import de.tum.cit.aet.hephaestus.practices.model.Verdict;
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
            target_type, target_id, contributor_id, subject_user_id,
            title, verdict, severity, confidence,
            evidence, reasoning, guidance,
            correlation_key, detected_at
        )
        VALUES (
            :id, :idempotencyKey, :agentJobId, :practiceId,
            :targetType, :targetId, :contributorId, :subjectUserId,
            :title, :verdict, :severity, :confidence,
            CAST(:evidence AS jsonb), :reasoning, :guidance,
            :correlationKey, :detectedAt
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
        @Param("targetType") String targetType,
        @Param("targetId") Long targetId,
        @Param("contributorId") Long contributorId,
        @Param("subjectUserId") Long subjectUserId,
        @Param("title") String title,
        @Param("verdict") String verdict,
        @Param("severity") String severity,
        @Param("confidence") Float confidence,
        @Param("evidence") String evidence,
        @Param("reasoning") String reasoning,
        @Param("guidance") String guidance,
        @Param("correlationKey") String correlationKey,
        @Param("detectedAt") Instant detectedAt
    );

    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM practice_finding WHERE practice_id IN (SELECT id FROM practice WHERE workspace_id = :workspaceId)",
        nativeQuery = true
    )
    void deleteAllByPracticeWorkspaceId(@Param("workspaceId") Long workspaceId);

    // Read queries for the contributor dashboard (Issue #896)

    /**
     * Paginated findings for a contributor within a workspace, with optional filters.
     *
     * <p>Workspace scoping is done via the {@code Practice.workspace} join.
     * Uses a separate {@code countQuery} because {@code JOIN FETCH} is incompatible
     * with count projections in Hibernate.
     */
    @Query(
        value = """
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.contributor.id = :contributorId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:verdict IS NULL OR f.verdict = :verdict)
        """,
        countQuery = """
        SELECT COUNT(f) FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.contributor.id = :contributorId
        AND p.workspace.id = :workspaceId
        AND (:practiceSlug IS NULL OR p.slug = :practiceSlug)
        AND (:verdict IS NULL OR f.verdict = :verdict)
        """
    )
    Page<PracticeFinding> findByContributorAndWorkspace(
        @Param("contributorId") Long contributorId,
        @Param("workspaceId") Long workspaceId,
        @Param("practiceSlug") String practiceSlug,
        @Param("verdict") Verdict verdict,
        Pageable pageable
    );

    /**
     * Per-practice aggregation: verdict counts and last finding date for a contributor.
     */
    @Query(
        """
        SELECT p.slug AS practiceSlug,
               p.name AS practiceName,
               p.category AS category,
               COUNT(f) AS totalFindings,
               SUM(CASE WHEN f.verdict = de.tum.cit.aet.hephaestus.practices.model.Verdict.OBSERVED THEN 1L ELSE 0L END) AS observedCount,
               SUM(CASE WHEN f.verdict = de.tum.cit.aet.hephaestus.practices.model.Verdict.NOT_OBSERVED THEN 1L ELSE 0L END) AS notObservedCount,
               MAX(f.detectedAt) AS lastFindingAt
        FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.contributor.id = :contributorId
        AND p.workspace.id = :workspaceId
        GROUP BY p.slug, p.name, p.category
        ORDER BY p.name ASC
        """
    )
    List<ContributorPracticeSummaryProjection> findSummaryByContributorAndWorkspace(
        @Param("contributorId") Long contributorId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Single finding by ID within a workspace, restricted to a specific contributor.
     *
     * <p>Ownership is enforced in the query (not in Java) to avoid lazy-load
     * fragility and to keep the auth check atomic with the fetch.
     */
    @Query(
        """
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.id = :findingId
        AND f.contributor.id = :contributorId
        AND p.workspace.id = :workspaceId
        """
    )
    Optional<PracticeFinding> findByIdAndContributorAndWorkspace(
        @Param("findingId") UUID findingId,
        @Param("contributorId") Long contributorId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * All findings for a specific pull request within a workspace.
     */
    @Query(
        """
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.targetType = :targetType
        AND f.targetId = :pullRequestId
        AND p.workspace.id = :workspaceId
        ORDER BY f.detectedAt DESC
        """
    )
    List<PracticeFinding> findByPullRequestAndWorkspace(
        @Param("targetType") WorkArtifact targetType,
        @Param("pullRequestId") Long pullRequestId,
        @Param("workspaceId") Long workspaceId
    );

    // Aggregation for agent context (Issue #895)

    /**
     * Returns aggregated verdict counts per practice for a contributor within a workspace.
     *
     * <p>Each row is one (practice slug, verdict) combination with the total count and the
     * most recent detection timestamp. Callers group results by slug to build a per-practice
     * history summary. The {@code idx_practice_finding_contributor_detected} index on
     * {@code (contributor_id, detected_at DESC)} narrows the initial scan by contributor.
     *
     * @param contributorId the contributor whose history to aggregate
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
        WHERE pf.contributor.id = :contributorId
          AND pf.practice.workspace.id = :workspaceId
        GROUP BY pf.practice.slug, pf.verdict
        ORDER BY pf.practice.slug, pf.verdict
        """
    )
    List<ContributorPracticeSummary> findContributorPracticeSummary(
        @Param("contributorId") Long contributorId,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Recent findings for a contributor within a workspace since a cutoff, newest first.
     *
     * <p>Used by the mentor's {@code findings_history.json} aspect: the agent needs to see
     * what specific findings have been delivered to the contributor lately so it can refer
     * to them by title in the conversation. Bounded by {@code limit} at the caller so the
     * page size stays a JPA concern.
     */
    @Query(
        """
        SELECT f FROM PracticeFinding f
        JOIN FETCH f.practice p
        WHERE f.contributor.id = :contributorId
          AND p.workspace.id = :workspaceId
          AND f.detectedAt >= :since
        ORDER BY f.detectedAt DESC
        """
    )
    List<PracticeFinding> findRecentByContributorAndWorkspace(
        @Param("contributorId") Long contributorId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since,
        org.springframework.data.domain.Pageable pageable
    );

    /**
     * Severity histogram for a contributor's findings within a workspace.
     * Returns {@code [severityName, count]} rows — caller maps to a name→count map.
     */
    @Query(
        """
        SELECT f.severity AS severity, COUNT(f) AS count
        FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.contributor.id = :contributorId
          AND p.workspace.id = :workspaceId
          AND f.detectedAt >= :since
        GROUP BY f.severity
        """
    )
    List<SeverityCount> countBySeverityForContributor(
        @Param("contributorId") Long contributorId,
        @Param("workspaceId") Long workspaceId,
        @Param("since") Instant since
    );

    /** Verdict histogram for a contributor's findings within a workspace. */
    @Query(
        """
        SELECT f.verdict AS verdict, COUNT(f) AS count
        FROM PracticeFinding f
        JOIN f.practice p
        WHERE f.contributor.id = :contributorId
          AND p.workspace.id = :workspaceId
          AND f.detectedAt >= :since
        GROUP BY f.verdict
        """
    )
    List<VerdictCount> countByVerdictForContributor(
        @Param("contributorId") Long contributorId,
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
        WHERE f.targetType = :targetType AND f.targetId = :targetId AND p.workspace.id = :workspaceId
          AND f.correlationKey IS NOT NULL
        GROUP BY f.agentJobId
        ORDER BY MAX(f.detectedAt) DESC, f.agentJobId DESC
        """
    )
    List<RunRef> findRecentRunRefsForTarget(
        @Param("targetType") WorkArtifact targetType,
        @Param("targetId") Long targetId,
        @Param("workspaceId") Long workspaceId,
        Pageable pageable
    );

    /** All correlation-keyed findings for the given (already-resolved) run job-ids, with the trend fields. */
    @Query(
        """
        SELECT f.agentJobId AS agentJobId, f.correlationKey AS correlationKey, f.verdict AS verdict,
               f.severity AS severity, f.confidence AS confidence, p.slug AS practiceSlug,
               f.title AS title, f.detectedAt AS detectedAt
        FROM PracticeFinding f JOIN f.practice p
        WHERE f.agentJobId IN :agentJobIds AND p.workspace.id = :workspaceId AND f.correlationKey IS NOT NULL
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
        String getCorrelationKey();
        Verdict getVerdict();
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
        de.tum.cit.aet.hephaestus.practices.model.Verdict getVerdict();
        Long getCount();
    }
}
