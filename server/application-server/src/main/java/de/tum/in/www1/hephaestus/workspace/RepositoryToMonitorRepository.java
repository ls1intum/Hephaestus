package de.tum.in.www1.hephaestus.workspace;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface RepositoryToMonitorRepository extends JpaRepository<RepositoryToMonitor, Long> {
    boolean existsByWorkspaceIdAndNameWithOwner(Long workspaceId, String nameWithOwner);
    Optional<RepositoryToMonitor> findByWorkspaceIdAndNameWithOwner(Long workspaceId, String nameWithOwner);
    List<RepositoryToMonitor> findByWorkspaceId(Long workspaceId);
}
