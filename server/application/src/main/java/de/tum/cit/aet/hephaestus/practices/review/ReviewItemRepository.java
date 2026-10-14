package de.tum.cit.aet.hephaestus.practices.review;

import de.tum.cit.aet.hephaestus.practices.review.model.ReviewItem;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewItemState;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewItemRepository extends JpaRepository<ReviewItem, ReviewItem.Id> {
    Optional<ReviewItem> findByWorkspaceIdAndReviewIdAndPracticeIdAndItemId(
            long workspaceId, UUID reviewId, long practiceId, long itemId);

    List<ReviewItem> findByWorkspaceIdAndReviewIdAndPracticeIdAndJobIdOrderByItemIdAsc(
            long workspaceId, UUID reviewId, long practiceId, UUID jobId);

    List<ReviewItem> findByWorkspaceIdAndResultIdOrderByReviewIdAscPracticeIdAscItemIdAsc(
            long workspaceId, UUID resultId);

    List<ReviewItem> findByWorkspaceIdAndReviewIdOrderByPracticeIdAscItemIdAsc(long workspaceId, UUID reviewId);

    List<ReviewItem> findByWorkspaceIdAndReviewIdAndStateOrderByPracticeIdAscItemIdAsc(
            long workspaceId, UUID reviewId, ReviewItemState state);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT i FROM ReviewItem i WHERE i.workspaceId = :workspaceId AND i.reviewId = :reviewId AND i.practiceId"
            + " = :practiceId AND i.itemId = :itemId")
    Optional<ReviewItem> lockAssignment(
            @Param("workspaceId") long workspaceId,
            @Param("reviewId") UUID reviewId,
            @Param("practiceId") long practiceId,
            @Param("itemId") long itemId);

    long countByWorkspaceIdAndReviewIdAndState(long workspaceId, UUID reviewId, ReviewItemState state);
}
