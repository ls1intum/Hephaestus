package de.tum.in.www1.hephaestus.practices;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace-scoped queries for pull requests in the practices context.
 *
 * <p>This repository lives in the practices package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor). The practices.detection subpackage
 * can depend on this repository since it's within the practices bounded context.
 */
@Repository
public interface PracticesPullRequestQueryRepository extends JpaRepository<PullRequest, Long> {
    /**
     * Finds all pull request IDs belonging to a workspace.
     * Used during workspace purge to cancel scheduled bad practice detection tasks.
     *
     * @param workspaceId the workspace ID
     * @return list of pull request IDs in the workspace
     */
    @Query(
        """
        SELECT p.id
        FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId
        """
    )
    List<Long> findPullRequestIdsByWorkspaceId(@Param("workspaceId") Long workspaceId);

    /**
     * Finds pull requests assigned to a user by login and states, scoped to a workspace.
     *
     * @param assigneeLogin the assignee's login (case-insensitive)
     * @param states the states to filter by
     * @param workspaceId the workspace to scope to
     * @return pull requests assigned to the user in the given states
     */
    @Transactional
    @Query(
        """
        SELECT p
        FROM PullRequest p
        LEFT JOIN FETCH p.labels
        JOIN FETCH p.author
        LEFT JOIN FETCH p.assignees
        LEFT JOIN FETCH p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE (p.author.login ILIKE :assigneeLogin OR LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM p.assignees u))
            AND p.state IN :states
            AND rtm.workspace.id = :workspaceId
        ORDER BY p.createdAt DESC
        """
    )
    List<PullRequest> findAssignedByLoginAndStates(
        @Param("assigneeLogin") String assigneeLogin,
        @Param("states") Set<PullRequest.State> states,
        @Param("workspaceId") Long workspaceId
    );
}
