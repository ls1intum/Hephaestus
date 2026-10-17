package de.tum.cit.aet.hephaestus.practices.review.schedule;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReviewScheduleRepository extends JpaRepository<ReviewSchedule, UUID> {
    Optional<ReviewSchedule> findByIdAndWorkspaceId(UUID id, long workspaceId);

    List<ReviewSchedule> findByWorkspaceIdOrderByKindAsc(long workspaceId);

    boolean existsByWorkspaceIdAndKind(long workspaceId, String kind);

    @WorkspaceAgnostic("The scheduler reads due schedules across workspaces")
    @Query("SELECT s FROM ReviewSchedule s WHERE s.enabled = TRUE AND s.nextRunAt <= :now ORDER BY s.nextRunAt, s.id")
    List<ReviewSchedule> findDue(@Param("now") Instant now, Pageable pageable);
}
