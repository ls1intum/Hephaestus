package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        LEFT JOIN FETCH prr.comments
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.author.login ILIKE :authorLogin
            AND prr.submittedAt >= :activitySince
            AND rtm.workspace.id = :workspaceId
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllByAuthorLoginSince(
        @Param("authorLogin") String authorLogin,
        @Param("activitySince") Instant activitySince,
        @Param("workspaceId") Long workspaceId
    );

    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        LEFT JOIN FETCH prr.comments
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.author.login ILIKE :authorLogin
            AND prr.submittedAt BETWEEN :after AND :before
            AND rtm.workspace.id = :workspaceId
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllByAuthorLoginInTimeframe(
        @Param("authorLogin") String authorLogin,
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("workspaceId") Long workspaceId
    );

    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        LEFT JOIN FETCH prr.comments
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
            AND rtm.workspace.id = :workspaceId
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframe(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("workspaceId") Long workspaceId
    );

    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest pr
        LEFT JOIN FETCH pr.repository r
        LEFT JOIN FETCH prr.comments
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
            AND rtm.workspace.id = :workspaceId
            AND EXISTS (
                SELECT 1
                FROM TeamRepositoryPermission trp
                JOIN trp.team t
                WHERE trp.repository = r
                AND t.id IN :teamIds
                AND trp.hiddenFromContributions = false
                AND (
                    NOT EXISTS (
                        SELECT l
                        FROM t.labels l
                        WHERE l.repository = r
                    )
                    OR
                    EXISTS (
                        SELECT l
                        FROM t.labels l
                        WHERE l.repository = r
                        AND l MEMBER OF pr.labels
                    )
                )
            )
            AND EXISTS (
                SELECT 1
                FROM TeamMembership tm
                WHERE tm.team.id IN :teamIds
                AND tm.user = prr.author
            )
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findAllInTimeframeOfTeams(
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("teamIds") Collection<Long> teamIds,
        @Param("workspaceId") Long workspaceId
    );

    @Query(
        value = """
        SELECT MIN(prr.submittedAt)
        FROM PullRequestReview prr
        JOIN prr.pullRequest pr
        JOIN pr.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE prr.author.id = :userId
            AND rtm.workspace.id = :workspaceId
        """
    )
    Instant findEarliestSubmissionInstant(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /**
     * Find all reviews for a specific pull request by a specific author.
     * Used for calculating per-PR XP (aggregating all reviews together for harmonic mean).
     * PullRequest ID inherently has workspace through repository.
     *
     * @param pullRequestId the pull request ID
     * @param authorId the author ID
     * @return list of reviews by this author on this PR
     */
    @Query(
        value = """
        SELECT prr
        FROM PullRequestReview prr
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.comments
        WHERE prr.pullRequest.id = :pullRequestId
            AND prr.author.id = :authorId
        ORDER BY prr.submittedAt ASC
        """
    )
    List<PullRequestReview> findByPullRequestIdAndAuthorId(
        @Param("pullRequestId") Long pullRequestId,
        @Param("authorId") Long authorId
    );
}
