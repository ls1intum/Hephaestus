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
        WHERE p.author.login ILIKE :authorLogin
            AND p.state IN :states
            AND rtm.workspace.id = :workspaceId
        ORDER BY p.createdAt DESC
        """
    )
    List<PullRequest> findAuthoredByLoginAndStates(
        @Param("authorLogin") String authorLogin,
        @Param("states") Set<PullRequest.State> states,
        @Param("workspaceId") Long workspaceId
    );

    @Transactional
    @Query(
        """
        SELECT p.author.id as authorId, COUNT(p) as count
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id IN :authorIds
            AND p.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue$State.OPEN
            AND p.createdAt >= :since
            AND p.createdAt < :until
            AND rtm.workspace.id = :workspaceId
        GROUP BY p.author.id
        """
    )
    java.util.List<AuthorCountProjection> countOpenPullRequestsByAuthors(
        @Param("workspaceId") Long workspaceId,
        @Param("authorIds") Set<Long> authorIds,
        @Param("since") java.time.Instant since,
        @Param("until") java.time.Instant until
    );

    @Transactional
    @Query(
        """
        SELECT p.author.id as authorId, COUNT(p) as count
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id IN :authorIds
            AND EXISTS (
                SELECT 1 FROM TeamMembership tm
                WHERE tm.user = p.author
                AND tm.team.id IN :teamIds
            )
            AND p.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue$State.OPEN
            AND p.createdAt >= :since
            AND p.createdAt < :until
            AND rtm.workspace.id = :workspaceId
            AND EXISTS (
                SELECT 1 FROM TeamRepositoryPermission trp
                WHERE trp.repository = p.repository
                AND trp.team.id IN :teamIds
            )
            AND NOT EXISTS (
                SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
                WHERE wtrs.repository = p.repository
                AND wtrs.workspace.id = :workspaceId
                AND wtrs.team.id IN :teamIds
                AND wtrs.hiddenFromContributions = true
            )
        GROUP BY p.author.id
        """
    )
    java.util.List<AuthorCountProjection> countOpenPullRequestsByAuthorsAndTeams(
        @Param("workspaceId") Long workspaceId,
        @Param("teamIds") Set<Long> teamIds,
        @Param("authorIds") Set<Long> authorIds,
        @Param("since") java.time.Instant since,
        @Param("until") java.time.Instant until
    );

    @Transactional
    @Query(
        """
        SELECT p.author.id as authorId, COUNT(p) as count
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id IN :authorIds
            AND p.mergedAt IS NOT NULL
            AND p.mergedAt >= :since
            AND p.mergedAt < :until
            AND rtm.workspace.id = :workspaceId
        GROUP BY p.author.id
        """
    )
    java.util.List<AuthorCountProjection> countMergedPullRequestsByAuthors(
        @Param("workspaceId") Long workspaceId,
        @Param("authorIds") Set<Long> authorIds,
        @Param("since") java.time.Instant since,
        @Param("until") java.time.Instant until
    );

    @Transactional
    @Query(
        """
        SELECT p.author.id as authorId, COUNT(p) as count
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id IN :authorIds
            AND EXISTS (
                SELECT 1 FROM TeamMembership tm
                WHERE tm.user = p.author
                AND tm.team.id IN :teamIds
            )
            AND p.mergedAt IS NOT NULL
            AND p.mergedAt >= :since
            AND p.mergedAt < :until
            AND rtm.workspace.id = :workspaceId
            AND EXISTS (
                SELECT 1 FROM TeamRepositoryPermission trp
                WHERE trp.repository = p.repository
                AND trp.team.id IN :teamIds
            )
            AND NOT EXISTS (
                SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
                WHERE wtrs.repository = p.repository
                AND wtrs.workspace.id = :workspaceId
                AND wtrs.team.id IN :teamIds
                AND wtrs.hiddenFromContributions = true
            )
        GROUP BY p.author.id
        """
    )
    java.util.List<AuthorCountProjection> countMergedPullRequestsByAuthorsAndTeams(
        @Param("workspaceId") Long workspaceId,
        @Param("teamIds") Set<Long> teamIds,
        @Param("authorIds") Set<Long> authorIds,
        @Param("since") java.time.Instant since,
        @Param("until") java.time.Instant until
    );

    @Transactional
    @Query(
        """
        SELECT p.author.id as authorId, COUNT(p) as count
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id IN :authorIds
            AND p.closedAt IS NOT NULL
            AND p.closedAt >= :since
            AND p.closedAt < :until
            AND p.isMerged = false
            AND rtm.workspace.id = :workspaceId
        GROUP BY p.author.id
        """
    )
    java.util.List<AuthorCountProjection> countClosedPullRequestsByAuthors(
        @Param("workspaceId") Long workspaceId,
        @Param("authorIds") Set<Long> authorIds,
        @Param("since") java.time.Instant since,
        @Param("until") java.time.Instant until
    );

    @Transactional
    @Query(
        """
        SELECT p.author.id as authorId, COUNT(p) as count
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id IN :authorIds
            AND EXISTS (
                SELECT 1 FROM TeamMembership tm
                WHERE tm.user = p.author
                AND tm.team.id IN :teamIds
            )
            AND p.closedAt IS NOT NULL
            AND p.closedAt >= :since
            AND p.closedAt < :until
            AND p.isMerged = false
            AND rtm.workspace.id = :workspaceId
            AND EXISTS (
                SELECT 1 FROM TeamRepositoryPermission trp
                WHERE trp.repository = p.repository
                AND trp.team.id IN :teamIds
            )
            AND NOT EXISTS (
                SELECT 1 FROM WorkspaceTeamRepositorySettings wtrs
                WHERE wtrs.repository = p.repository
                AND wtrs.workspace.id = :workspaceId
                AND wtrs.team.id IN :teamIds
                AND wtrs.hiddenFromContributions = true
            )
        GROUP BY p.author.id
        """
    )
    java.util.List<AuthorCountProjection> countClosedPullRequestsByAuthorsAndTeams(
        @Param("workspaceId") Long workspaceId,
        @Param("teamIds") Set<Long> teamIds,
        @Param("authorIds") Set<Long> authorIds,
        @Param("since") java.time.Instant since,
        @Param("until") java.time.Instant until
    );

    interface AuthorCountProjection {
        Long getAuthorId();
        Long getCount();
    }
}
