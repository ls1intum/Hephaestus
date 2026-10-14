package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Result;
import de.tum.cit.aet.hephaestus.practices.spi.ResultEndReason;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ResultRepository extends JpaRepository<Result, UUID> {
    Optional<Result> findByIdAndWorkspaceId(UUID id, long workspaceId);

    Optional<Result> findByIdAndDeveloperIdAndWorkspaceId(UUID id, long developerId, long workspaceId);

    List<Result> findByWorkspaceIdAndReviewIdOrderByRecordedAtAscIdAsc(long workspaceId, UUID reviewId);

    List<Result> findByWorkspaceIdAndItemIdOrderByRecordedAtDescIdDesc(long workspaceId, long itemId);

    List<Result> findByWorkspaceIdAndIdIn(long workspaceId, Collection<UUID> ids);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Result r WHERE r.id = :id AND r.workspaceId = :workspaceId")
    Optional<Result> lockByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") long workspaceId);

    @Query("""
        SELECT r FROM Result r
        WHERE r.workspaceId = :workspaceId AND r.practiceId = :practiceId
          AND r.itemId = :itemId AND r.endedAt IS NULL
          AND (r.developerId = :developerId OR (r.developerId IS NULL AND :developerId IS NULL))
        """)
    Optional<Result> findCurrentClaim(
            @Param("workspaceId") long workspaceId,
            @Param("practiceId") long practiceId,
            @Param("itemId") long itemId,
            @Param("developerId") @Nullable Long developerId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Result r SET r.endedAt = :at, r.endReason = :reason
        WHERE r.workspaceId = :workspaceId AND r.itemId = :itemId AND r.endedAt IS NULL
        """)
    int endCurrent(
            @Param("workspaceId") long workspaceId,
            @Param("itemId") long itemId,
            @Param("reason") ResultEndReason reason,
            @Param("at") Instant at);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
        UPDATE Result r SET r.endedAt = :at, r.endReason = :reason
        WHERE r.workspaceId = :workspaceId AND r.id = :id AND r.endedAt IS NULL
        """)
    int endById(
            @Param("workspaceId") long workspaceId,
            @Param("id") UUID id,
            @Param("reason") ResultEndReason reason,
            @Param("at") Instant at);

    @Query("""
        SELECT r FROM Result r
        WHERE r.workspaceId = :workspaceId AND r.developerId = :developerId
          AND r.endedAt IS NULL
          AND NOT EXISTS (SELECT m.id FROM ResultMark m WHERE m.resultId = r.id
              AND m.workspaceId = r.workspaceId AND m.kind = :incorrect AND m.removedAt IS NULL)
        ORDER BY r.recordedAt DESC, r.id DESC
        """)
    Page<Result> findCurrentForDeveloper(
            @Param("workspaceId") long workspaceId,
            @Param("developerId") long developerId,
            @Param("incorrect") ResultMarkKind incorrect,
            Pageable pageable);

    @Query(value = """
        SELECT r.* FROM result r
        JOIN review_practice prior_method ON prior_method.review_id = r.review_id
          AND prior_method.practice_id = r.practice_id AND prior_method.workspace_id = r.workspace_id
        JOIN review_practice requested_method ON requested_method.review_id = :reviewId
          AND requested_method.practice_id = r.practice_id AND requested_method.workspace_id = r.workspace_id
        WHERE r.workspace_id = :workspaceId AND r.practice_id = :practiceId AND r.item_id = :itemId
          AND r.practice_version_id = :practiceVersionId AND r.item_version = :itemVersion
          AND r.developer_id IS NOT DISTINCT FROM :developerId
          AND r.evidence_version = 1 AND r.ended_at IS NULL
          AND r.summary IS NOT NULL AND r.explanation IS NOT NULL AND r.evidence IS NOT NULL AND r.coverage IS NOT NULL
          AND prior_method.method IS NOT NULL
          AND prior_method.method->>'modelDigest' = requested_method.method->>'modelDigest'
          AND prior_method.method->>'epoch' = requested_method.method->>'epoch'
          AND NOT EXISTS (SELECT 1 FROM result_mark m WHERE m.result_id = r.id
            AND m.workspace_id = r.workspace_id AND m.kind = 'INCORRECT' AND m.removed_at IS NULL)
        """, nativeQuery = true)
    Optional<Result> findReuseCandidate(
            @Param("workspaceId") long workspaceId,
            @Param("reviewId") UUID reviewId,
            @Param("practiceId") long practiceId,
            @Param("practiceVersionId") long practiceVersionId,
            @Param("itemId") long itemId,
            @Param("itemVersion") String itemVersion,
            @Param("developerId") @Nullable Long developerId);
}
