package de.tum.cit.aet.hephaestus.practices.review.schedule;

import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewBatchTargetRepository extends JpaRepository<ReviewBatchTarget, ReviewBatchTarget.Id> {
    List<ReviewBatchTarget> findByWorkspaceIdAndBatchIdAndItemIdGreaterThanOrderByItemIdAsc(
            long workspaceId, UUID batchId, long afterItemId, Pageable page);

    List<ReviewBatchTarget> findByWorkspaceIdAndBatchIdOrderByItemIdAsc(long workspaceId, UUID batchId);
}
