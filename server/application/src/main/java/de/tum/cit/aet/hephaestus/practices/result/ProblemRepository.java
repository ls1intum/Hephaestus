package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Problem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {
    Optional<Problem> findByIdAndWorkspaceId(UUID id, long workspaceId);

    List<Problem> findByWorkspaceIdAndResultIdOrderByPositionAsc(long workspaceId, UUID resultId);

    List<Problem> findByWorkspaceIdAndResultIdInOrderByResultIdAscPositionAsc(
            long workspaceId, Collection<UUID> resultIds);

    List<Problem> findByWorkspaceIdAndIdIn(long workspaceId, Collection<UUID> ids);

    @Query("""
        SELECT p FROM Problem p JOIN Result r ON r.id = p.resultId AND r.workspaceId = p.workspaceId
        WHERE p.workspaceId = :workspaceId AND r.endedAt IS NULL
          AND NOT EXISTS (SELECT m.id FROM ResultMark m WHERE m.workspaceId = r.workspaceId
            AND m.resultId = r.id AND m.removedAt IS NULL)
        ORDER BY r.recordedAt DESC, p.position ASC
        """)
    Page<Problem> findOpen(@Param("workspaceId") long workspaceId, Pageable pageable);
}
