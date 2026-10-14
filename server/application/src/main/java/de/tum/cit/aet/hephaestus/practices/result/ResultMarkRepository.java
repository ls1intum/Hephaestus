package de.tum.cit.aet.hephaestus.practices.result;

import de.tum.cit.aet.hephaestus.practices.model.ResultMark;
import de.tum.cit.aet.hephaestus.practices.spi.ResultMarkKind;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResultMarkRepository extends JpaRepository<ResultMark, UUID> {
    Optional<ResultMark> findByIdAndResultIdAndWorkspaceId(UUID id, UUID resultId, long workspaceId);

    Optional<ResultMark> findByWorkspaceIdAndResultIdAndKindAndRemovedAtIsNull(
            long workspaceId, UUID resultId, ResultMarkKind kind);

    List<ResultMark> findByWorkspaceIdAndResultIdOrderByCreatedAtDescIdDesc(long workspaceId, UUID resultId);

    List<ResultMark> findByWorkspaceIdAndResultIdInAndRemovedAtIsNull(long workspaceId, Collection<UUID> resultIds);

    boolean existsByWorkspaceIdAndResultIdAndKindAndRemovedAtIsNull(
            long workspaceId, UUID resultId, ResultMarkKind kind);
}
