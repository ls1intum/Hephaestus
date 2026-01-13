package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Workspace-scoped queries for repositories.
 *
 * <p>This repository lives in the workspace package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor). The gitprovider package should not
 * depend on workspace entities to maintain clean architecture boundaries.
 *
 * <p>Used by workspace services and sync operations that need to iterate over
 * repositories monitored by a workspace.
 */
@org.springframework.stereotype.Repository
public interface WorkspaceRepositoryQueryRepository extends JpaRepository<Repository, Long> {
    /**
     * Finds all active (monitored) repositories in a workspace.
     *
     * @param workspaceId the workspace to scope to
     * @return all repositories monitored by the workspace
     */
    @Query(
        """
        SELECT r
        FROM Repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId
        ORDER BY r.name ASC
        """
    )
    List<Repository> findActiveByWorkspaceId(@Param("workspaceId") Long workspaceId);
}
