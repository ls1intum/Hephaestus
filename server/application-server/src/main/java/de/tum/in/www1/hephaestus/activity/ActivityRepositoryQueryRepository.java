package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Workspace-scoped queries for repositories in the activity backfill context.
 *
 * <p>This repository lives in the activity package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor). The gitprovider package should not
 * depend on workspace entities to maintain clean architecture boundaries.
 */
@org.springframework.stereotype.Repository
public interface ActivityRepositoryQueryRepository extends JpaRepository<Repository, Long> {

    /**
     * Finds all active (monitored) repositories in a workspace.
     * Used by backfill operations to iterate over workspace repositories.
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
