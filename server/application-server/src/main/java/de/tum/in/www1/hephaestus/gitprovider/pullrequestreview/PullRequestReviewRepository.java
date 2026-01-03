package de.tum.in.www1.hephaestus.gitprovider.pullrequestreview;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
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
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE prr.author.login ILIKE :authorLogin
            AND prr.submittedAt >= :activitySince
            AND prr.pullRequest.repository.organization.workspaceId = :workspaceId
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
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE prr.author.login ILIKE :authorLogin
            AND prr.submittedAt BETWEEN :after AND :before
            AND prr.pullRequest.repository.organization.workspaceId = :workspaceId
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
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
            AND prr.pullRequest.repository.organization.workspaceId = :workspaceId
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
        LEFT JOIN FETCH prr.pullRequest
        LEFT JOIN FETCH prr.pullRequest.repository
        LEFT JOIN FETCH prr.comments
        WHERE
            prr.submittedAt BETWEEN :after AND :before
            AND prr.author.type = 'USER'
            AND prr.pullRequest.repository.organization.workspaceId = :workspaceId
            AND EXISTS (
                SELECT 1
                FROM TeamRepositoryPermission trp
                JOIN trp.team t
                WHERE trp.repository = prr.pullRequest.repository
                AND t.id IN :teamIds
                AND trp.hiddenFromContributions = false
                AND (
                    NOT EXISTS (
                        SELECT l
                        FROM t.labels l
                        WHERE l.repository = prr.pullRequest.repository
                    )
                    OR
                    EXISTS (
                        SELECT l
                        FROM t.labels l
                        WHERE l.repository = prr.pullRequest.repository
                        AND l MEMBER OF prr.pullRequest.labels
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
        WHERE prr.author.id = :userId
            AND prr.pullRequest.repository.organization.workspaceId = :workspaceId
        """
    )
    Instant findEarliestSubmissionInstant(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /**
     * Find all reviews for a specific pull request by a specific author.
     * Used for calculating per-PR XP (aggregating all reviews together for harmonic mean).
     * PullRequest ID inherently has workspace through repository.organization.workspaceId.
     *
     * @param pullRequestId the pull request ID
     * @param authorId the author ID
     * @return list of reviews by this author on this PR
     */
    @WorkspaceAgnostic("PullRequest ID has workspace through repository.organization")
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
