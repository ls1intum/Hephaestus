package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.gitprovider.repository.Repository;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Workspace-scoped queries for repositories in the profile context.
 *
 * <p>This repository lives in the profile package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor). The gitprovider package should not
 * depend on workspace entities to maintain clean architecture.
 *
 * <p>These queries are used for user profile display where workspace context is required.
 */
@org.springframework.stereotype.Repository
public interface ProfileRepositoryQueryRepository extends JpaRepository<Repository, Long> {
    /**
     * Finds all repositories a user has contributed to within a workspace.
     *
     * @param contributorLogin the contributor's login (case-insensitive)
     * @param workspaceId the workspace to scope to
     * @return repositories the user has contributed to (via pull requests)
     */
    @Query(
        """
        SELECT DISTINCT r
        FROM Repository r
        JOIN PullRequest pr ON r.id = pr.repository.id
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE pr.author.login ILIKE :contributorLogin
            AND rtm.workspace.id = :workspaceId
        ORDER BY r.name ASC
        """
    )
    List<Repository> findContributedByLogin(
        @Param("contributorLogin") String contributorLogin,
        @Param("workspaceId") Long workspaceId
    );

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
