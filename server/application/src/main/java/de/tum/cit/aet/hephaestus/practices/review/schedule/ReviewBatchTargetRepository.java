package de.tum.cit.aet.hephaestus.practices.review.schedule;

import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewBatchTargetRepository extends JpaRepository<ReviewBatchTarget, ReviewBatchTarget.Id> {
    List<ReviewBatchTarget> findByWorkspaceIdAndBatchIdOrderByItemIdAsc(long workspaceId, UUID batchId);
}
