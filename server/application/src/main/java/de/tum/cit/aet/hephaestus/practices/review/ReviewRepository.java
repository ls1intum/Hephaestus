package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.review.model.Review;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewKind;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewStatus;
import jakarta.persistence.LockModeType;
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

public interface ReviewRepository extends JpaRepository<Review, UUID> {
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
