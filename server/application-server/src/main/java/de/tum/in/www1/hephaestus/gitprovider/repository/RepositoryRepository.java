package de.tum.in.www1.hephaestus.gitprovider.repository;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

@org.springframework.stereotype.Repository
public interface RepositoryRepository extends JpaRepository<Repository, Long> {
    /**
     * Finds a repository by its full name (owner/name).
     * Used during sync operations to check if repository exists.
     */
    @WorkspaceAgnostic("Sync operation - lookup by external GitHub identifier")
    Optional<Repository> findByNameWithOwner(String nameWithOwner);

    List<Repository> findByNameWithOwnerStartingWithIgnoreCase(String prefix);

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
