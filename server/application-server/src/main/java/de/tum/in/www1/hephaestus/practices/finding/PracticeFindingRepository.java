package de.tum.in.www1.hephaestus.practices.finding;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.practices.finding.dto.ContributorPracticeSummaryProjection;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import de.tum.in.www1.hephaestus.practices.model.Verdict;
import java.time.Instant;
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
            target_type, target_id, contributor_id,
            title, verdict, severity, confidence,
            evidence, reasoning, guidance, guidance_method,
            detected_at
        )
        VALUES (
            :id, :idempotencyKey, :agentJobId, :practiceId,
            :targetType, :targetId, :contributorId,
            :title, :verdict, :severity, :confidence,
            CAST(:evidence AS jsonb), :reasoning, :guidance, :guidanceMethod,
            :detectedAt
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
        @Param("title") String title,
        @Param("verdict") String verdict,
        @Param("severity") String severity,
        @Param("confidence") Float confidence,
        @Param("evidence") String evidence,
        @Param("reasoning") String reasoning,
        @Param("guidance") String guidance,
        @Param("guidanceMethod") String guidanceMethod,
        @Param("detectedAt") Instant detectedAt
    );

    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM practice_finding WHERE practice_id IN (SELECT id FROM practice WHERE workspace_id = :workspaceId)",
        nativeQuery = true
    )
    void deleteAllByPracticeWorkspaceId(@Param("workspaceId") Long workspaceId);

    // ══════════════════════════════════════════════════════════════════════════
    // Read queries for the contributor dashboard (Issue #896)
    // ══════════════════════════════════════════════════════════════════════════

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
               SUM(CASE WHEN f.verdict = de.tum.in.www1.hephaestus.practices.model.Verdict.POSITIVE THEN 1L ELSE 0L END) AS positiveCount,
               SUM(CASE WHEN f.verdict = de.tum.in.www1.hephaestus.practices.model.Verdict.NEGATIVE THEN 1L ELSE 0L END) AS negativeCount,
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
        WHERE f.targetType = 'pull_request'
        AND f.targetId = :pullRequestId
        AND p.workspace.id = :workspaceId
        ORDER BY f.detectedAt DESC
        """
    )
    List<PracticeFinding> findByPullRequestAndWorkspace(
        @Param("pullRequestId") Long pullRequestId,
        @Param("workspaceId") Long workspaceId
    );

    // ══════════════════════════════════════════════════════════════════════════
    // Aggregation for agent context (Issue #895)
    // ══════════════════════════════════════════════════════════════════════════

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
}
