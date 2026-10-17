package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.Problem;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemRepository extends JpaRepository<Problem, UUID> {
    Optional<Problem> findByIdAndWorkspaceId(UUID id, long workspaceId);

    List<Problem> findByWorkspaceIdAndResultIdOrderByPositionAsc(long workspaceId, UUID resultId);

    List<Problem> findByWorkspaceIdAndResultIdInOrderByResultIdAscPositionAsc(
            long workspaceId, Collection<UUID> resultIds);

    List<Problem> findByWorkspaceIdAndIdIn(long workspaceId, Collection<UUID> ids);
}
