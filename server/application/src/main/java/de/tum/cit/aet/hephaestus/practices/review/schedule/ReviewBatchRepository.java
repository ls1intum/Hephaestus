package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchPauseReason;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewBatchRepository extends JpaRepository<ReviewBatch, UUID> {
    Page<ReviewBatch> findByWorkspaceId(long workspaceId, Pageable pageable);

    Optional<ReviewBatch> findByIdAndWorkspaceId(UUID id, long workspaceId);

    List<ReviewBatch> findByWorkspaceIdOrderByCreatedAtDesc(long workspaceId, Pageable pageable);

    boolean existsByWorkspaceIdAndStatusIn(long workspaceId, Collection<ReviewBatchStatus> statuses);

    @WorkspaceAgnostic("The driver rechecks budget pauses across workspaces")
    List<ReviewBatch> findByStatusAndPauseReasonOrderByUpdatedAtAscIdAsc(
            ReviewBatchStatus status, ReviewBatchPauseReason reason, Pageable pageable);

    @WorkspaceAgnostic("The driver reads active batches across workspaces")
    List<ReviewBatch> findByStatusOrderByUpdatedAtAscIdAsc(ReviewBatchStatus status, Pageable pageable);
}
