package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.review.report.ScanMembershipQueries;
import de.tum.cit.aet.hephaestus.practices.spi.FeedbackState;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewOrigin;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import jakarta.persistence.LockModeType;
import java.math.BigDecimal;
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
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewRepository extends JpaRepository<Review, UUID>, ReviewQueries, ScanMembershipQueries {
    @Query(value = """
        SELECT COUNT(*) FILTER(WHERE outcome='MET') AS met,COUNT(*) FILTER(WHERE outcome='NOT_MET') AS notMet,
          COUNT(*) FILTER(WHERE outcome='DOES_NOT_APPLY') AS doesNotApply,
          COUNT(*) FILTER(WHERE outcome='COULD_NOT_DETERMINE') AS couldNotDetermine
        FROM result r WHERE r.workspace_id=:ws AND ((:scan AND EXISTS(SELECT 1 FROM review_item member
          WHERE member.workspace_id=r.workspace_id AND member.review_id=:id AND member.result_id=r.id))
          OR (NOT :scan AND r.review_id=:id))
          AND (CAST(:practice AS bigint) IS NULL OR r.practice_id=:practice)
        """, nativeQuery = true)
    ResultCounts resultCounts(long ws, UUID id, boolean scan, @Nullable Long practice);

    @Query(
            value = "SELECT state,count(*) AS count FROM feedback WHERE workspace_id=:ws AND review_id=:id GROUP BY"
                    + " state",
            nativeQuery = true)
    List<FeedbackStateCount> feedbackCounts(long ws, UUID id);

    interface ResultCounts {
        long getMet();

        long getNotMet();

        long getDoesNotApply();

        long getCouldNotDetermine();
    }

    interface FeedbackStateCount {
        FeedbackState getState();

        long getCount();
    }

    @WorkspaceAgnostic("The scan driver discovers active work across workspaces before scoped assignment checks")
    @Query(
            value = "SELECT * FROM review WHERE kind='SCAN' AND status IN ('QUEUED','RUNNING')"
                    + " ORDER BY created_at,id::text",
            nativeQuery = true)
    List<Review> activeScans(Pageable page);

    @WorkspaceAgnostic("Selects workspaces with expired proof; each workspace is erased in its own scoped transaction")
    @Query(value = """
        SELECT DISTINCT workspace_id FROM review WHERE kind='SCAN' AND completed_at<=:cutoff AND
        COALESCE((report->>'expired')::boolean,false)=false UNION SELECT DISTINCT workspace_id
        FROM result WHERE ended_at<=:cutoff ORDER BY workspace_id
        """, nativeQuery = true)
    List<Long> workspacesWithExpiredProof(Instant cutoff);

    @Query(value = """
        SELECT id FROM review WHERE workspace_id=:workspaceId AND kind='SCAN' AND completed_at<=:cutoff
        AND COALESCE((report->>'expired')::boolean,false)=false ORDER BY id::text
        """, nativeQuery = true)
    List<UUID> expiredScanIds(long workspaceId, Instant cutoff);

    @Query(value = """
        WITH costs AS (SELECT q.id,sum(u.cost_usd) AS cost,bool_or(u.pricing_state='UNPRICED') AS unpriced
          FROM review q JOIN item i ON i.id=q.item_id AND i.workspace_id=q.workspace_id
          JOIN llm_usage_event u ON u.review_id=q.id AND u.workspace_id=q.workspace_id
          WHERE q.workspace_id=:workspaceId AND i.kind=:itemKind AND q.status IN ('COMPLETE','PARTIAL')
            AND q.completed_at>=:since GROUP BY q.id)
        SELECT CASE WHEN bool_or(unpriced) THEN NULL ELSE avg(cost) END FROM costs
        """, nativeQuery = true)
    @Nullable
    BigDecimal meanCostPerReviewUsd(long workspaceId, String itemKind, Instant since);

    long countByWorkspaceIdAndCreatedAtGreaterThanEqual(long workspaceId, Instant since);

    long countByWorkspaceIdAndRequestedByIdAndOriginAndCreatedAtGreaterThanEqual(
            long workspaceId, long accountId, ReviewOrigin origin, Instant since);

    boolean existsByWorkspaceIdAndItemIdAndOriginAndCreatedAtGreaterThanEqual(
            long workspaceId, long itemId, ReviewOrigin origin, Instant since);

    boolean existsByWorkspaceIdAndItemIdAndStatusIn(long workspaceId, long itemId, Collection<ReviewStatus> statuses);

    @Query(value = "SELECT review_model_digest(CAST(:models AS jsonb))", nativeQuery = true)
    String modelDigest(@Param("models") String models);

    Optional<Review> findByIdAndWorkspaceId(UUID id, long workspaceId);

    Optional<Review> findByWorkspaceIdAndRequestKey(long workspaceId, UUID requestKey);

    Optional<Review> findByWorkspaceIdAndEventId(long workspaceId, UUID eventId);

    boolean existsByWorkspaceIdAndBatchIdAndItemIdAndStatusNot(
            long workspaceId, UUID batchId, long itemId, ReviewStatus status);

    List<Review> findByWorkspaceIdAndBatchIdOrderByCreatedAtAsc(long workspaceId, UUID batchId);

    List<Review> findByWorkspaceIdAndItemIdOrderByOrderedAtDesc(long workspaceId, long itemId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT r FROM Review r WHERE r.id = :id AND r.workspaceId = :workspaceId")
    Optional<Review> lockByIdAndWorkspaceId(@Param("id") UUID id, @Param("workspaceId") long workspaceId);

    @Query("SELECT r FROM Review r WHERE r.workspaceId = :workspaceId AND r.kind = :kind ORDER BY r.createdAt DESC,"
            + " r.id DESC")
    Page<Review> findByWorkspaceAndKind(
            @Param("workspaceId") long workspaceId, @Param("kind") ReviewKind kind, Pageable pageable);

    @Query("SELECT r FROM Review r WHERE r.workspaceId=:workspaceId AND r.kind=:kind AND (:repositoryItemId IS NULL"
            + " OR r.itemId=:repositoryItemId) AND (:status IS NULL OR r.status=:status) ORDER BY r.createdAt"
            + " DESC,r.id DESC")
    Page<Review> findScans(
            @Param("kind") ReviewKind kind,
            @Param("workspaceId") long workspaceId,
            @Param("repositoryItemId") @Nullable Long repositoryItemId,
            @Param("status") @Nullable ReviewStatus status,
            Pageable pageable);

    Optional<Review> findFirstByWorkspaceIdAndKindAndStatusInOrderByCreatedAtAsc(
            long workspaceId, ReviewKind kind, Collection<ReviewStatus> statuses);

    boolean existsByWorkspaceIdAndKindAndStatusIn(long workspaceId, ReviewKind kind, Collection<ReviewStatus> statuses);
}
