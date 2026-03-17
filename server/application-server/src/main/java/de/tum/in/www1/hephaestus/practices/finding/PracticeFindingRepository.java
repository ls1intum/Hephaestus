package de.tum.in.www1.hephaestus.practices.finding;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.practices.model.PracticeFinding;
import java.time.Instant;
import java.util.UUID;
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
            verdict, confidence, evidence, reasoning,
            guidance, guidance_method, file_path,
            start_line, end_line, detected_at
        )
        VALUES (
            :id, :idempotencyKey, :agentJobId, :practiceId,
            :targetType, :targetId, :contributorId,
            :verdict, :confidence, CAST(:evidence AS jsonb), :reasoning,
            :guidance, :guidanceMethod, :filePath,
            :startLine, :endLine, :detectedAt
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
        @Param("verdict") String verdict,
        @Param("confidence") float confidence,
        @Param("evidence") String evidence,
        @Param("reasoning") String reasoning,
        @Param("guidance") String guidance,
        @Param("guidanceMethod") String guidanceMethod,
        @Param("filePath") String filePath,
        @Param("startLine") Integer startLine,
        @Param("endLine") Integer endLine,
        @Param("detectedAt") Instant detectedAt
    );

    @Modifying
    @Transactional
    @Query(
        value = "DELETE FROM practice_finding WHERE practice_id IN (SELECT id FROM practice WHERE workspace_id = :workspaceId)",
        nativeQuery = true
    )
    void deleteAllByPracticeWorkspaceId(@Param("workspaceId") Long workspaceId);
}
