package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.practices.spi.ReviewBatchStatus;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewBatchRepository extends JpaRepository<ReviewBatch, UUID> {
    Page<ReviewBatch> findByWorkspaceId(long workspaceId, Pageable pageable);

    Optional<ReviewBatch> findByIdAndWorkspaceId(UUID id, long workspaceId);

    List<ReviewBatch> findByWorkspaceIdOrderByCreatedAtDesc(long workspaceId, Pageable pageable);

    boolean existsByWorkspaceIdAndStatusIn(long workspaceId, Collection<ReviewBatchStatus> statuses);

    @WorkspaceAgnostic("The driver reads active batches across workspaces")
    @Query("SELECT b FROM ReviewBatch b WHERE b.status IN :statuses ORDER BY b.updatedAt, b.id")
    List<ReviewBatch> findByStatusIn(@Param("statuses") Collection<ReviewBatchStatus> statuses, Pageable pageable);
}
