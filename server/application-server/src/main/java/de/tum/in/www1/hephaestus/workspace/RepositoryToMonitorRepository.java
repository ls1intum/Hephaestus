package de.tum.in.www1.hephaestus.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositoryToMonitorRepository extends JpaRepository<RepositoryToMonitor, Long> {
    Optional<RepositoryToMonitor> findByWorkspaceIdAndNameWithOwnerIgnoreCaseAndSource(
        Long workspaceId,
        String nameWithOwner,
        RepositoryToMonitor.Source source
    );

    Optional<RepositoryToMonitor> findByWorkspaceIdAndRepository_IdAndSource(
        Long workspaceId,
        Long repositoryId,
        RepositoryToMonitor.Source source
    );

    List<RepositoryToMonitor> findAllByWorkspaceIdAndActiveTrue(Long workspaceId);
}
