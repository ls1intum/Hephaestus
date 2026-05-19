package de.tum.in.www1.hephaestus.workspace;

import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.time.Instant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Workspace-scoped queries for contribution metadata.
 *
 * <p>This repository provides queries to find the earliest contributions
 * for users within a workspace context. It joins gitprovider entities
 * (PullRequest, PullRequestReview, IssueComment) with workspace entities
 * (RepositoryToMonitor) to scope results to monitored repositories.
 *
 * <p>Note: This repository uses PullRequest as the entity type but provides
 * aggregate queries across multiple contribution types.
 */
@Repository
public interface WorkspaceContributionQueryRepository extends JpaRepository<PullRequest, Long> {
    /**
     * Finds the earliest pull request creation date for a user in a workspace.
     *
     * @param workspaceId the workspace to scope to
     * @param userId the user ID
     * @return the earliest creation instant, or null if no PRs exist
     */
    @Query(
        """
        SELECT MIN(p.createdAt)
        FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE p.author.id = :userId
            AND rtm.workspace.id = :workspaceId
        """
    )
    Instant findEarliestPullRequestCreatedAt(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /**
     * Finds the earliest review submission instant for a user in a workspace.
     *
     * @param workspaceId the workspace to scope to
     * @param userId the user ID
     * @return the earliest submission instant, or null if no reviews exist
     */
    @Query(
        """
        SELECT MIN(r.submittedAt)
        FROM PullRequestReview r
        JOIN r.pullRequest p
        JOIN p.repository repo
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = repo.nameWithOwner
        WHERE r.author.id = :userId
            AND rtm.workspace.id = :workspaceId
        """
    )
    Instant findEarliestReviewSubmittedAt(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /**
     * Finds the earliest issue comment creation date for a user in a workspace.
     *
     * @param workspaceId the workspace to scope to
     * @param userId the user ID
     * @return the earliest creation instant, or null if no comments exist
     */
    @Query(
        """
        SELECT MIN(c.createdAt)
        FROM IssueComment c
        JOIN c.issue i
        JOIN i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE c.author.id = :userId
            AND rtm.workspace.id = :workspaceId
        """
    )
    Instant findEarliestCommentCreatedAt(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);
}
