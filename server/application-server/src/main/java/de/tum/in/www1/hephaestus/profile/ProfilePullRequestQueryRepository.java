package de.tum.in.www1.hephaestus.profile;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.List;
import java.util.Set;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Workspace-scoped queries for pull requests in the profile context.
 *
 * <p>This repository lives in the profile package because it joins gitprovider entities
 * with workspace entities (RepositoryToMonitor). The gitprovider package should not
 * depend on workspace entities to maintain clean architecture.
 *
 * <p>These queries are used for user profile display where workspace context is required.
 */
@Repository
public interface ProfilePullRequestQueryRepository extends JpaRepository<PullRequest, Long> {
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
