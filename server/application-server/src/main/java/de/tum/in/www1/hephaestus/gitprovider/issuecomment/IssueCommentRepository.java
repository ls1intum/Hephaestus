package de.tum.in.www1.hephaestus.gitprovider.issuecomment;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for IssueComment entities.
 */
@Repository
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {
    Optional<IssueComment> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    /**
     * Batch fetch comments by IDs with all related entities eagerly loaded.
     *
     * <p>Used by the profile module to hydrate ActivityEvent target entities.
     * Fetches author, issue, and repository in one query to avoid N+1.
     *
     * @param ids the comment IDs to fetch
     * @return comments with related entities eagerly loaded
     */
    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue i
        LEFT JOIN FETCH i.repository
        WHERE ic.id IN :ids
        """
    )
    List<IssueComment> findAllByIdWithRelations(@Param("ids") Collection<Long> ids);

    /**
     * Find all issue comments by a specific author within a time range, scoped to a workspace.
     *
     * <p>Used by the profile module to show all comment activity directly from the source,
     * independent of ActivityEvent records.
     *
     * @param authorLogin the login of the comment author
     * @param after start of time range (inclusive)
     * @param before end of time range (exclusive)
     * @param onlyFromPullRequests if true, only return comments on pull requests
     * @param workspaceId the workspace to scope the query to
     * @return comments with related entities eagerly loaded, ordered by createdAt descending
     */
    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        LEFT JOIN FETCH ic.issue i
        LEFT JOIN FETCH i.repository repo
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = repo.nameWithOwner
        WHERE ic.author.login = :authorLogin
            AND ic.createdAt >= :after
            AND ic.createdAt < :before
            AND ic.author.type = 'USER'
            AND rtm.workspace.id = :workspaceId
            AND (:onlyFromPullRequests = false OR i.htmlUrl LIKE '%/pull/%')
        ORDER BY ic.createdAt DESC
        """
    )
    List<IssueComment> findAllByAuthorLoginInTimeframe(
        @Param("authorLogin") String authorLogin,
        @Param("after") Instant after,
        @Param("before") Instant before,
        @Param("onlyFromPullRequests") boolean onlyFromPullRequests,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Count distinct comment authors on an issue.
     * Used by HiveMind achievement evaluator.
     */
    @Query(
        """
        SELECT COUNT(DISTINCT ic.author.id)
        FROM IssueComment ic
        WHERE ic.issue.id = :issueId
        """
    )
    long countDistinctAuthorIdsByIssueId(@Param("issueId") Long issueId);

    /**
     * Count distinct participants on an issue (union of comment authors and issue author).
     * Uses UNION to deduplicate when the issue author also commented.
     * Used by HiveMind achievement evaluator.
     */
    @Query(
        value = """
        SELECT COUNT(*) FROM (
            SELECT DISTINCT author_id FROM issue_comment WHERE issue_id = :issueId AND author_id IS NOT NULL
            UNION
            SELECT author_id FROM issue WHERE id = :issueId AND author_id IS NOT NULL
        ) AS participants
        """,
        nativeQuery = true
    )
    long countDistinctParticipantsByIssueId(@Param("issueId") Long issueId);

    /**
     * Count comments on an issue NOT authored by a specific user.
     * Used by Necromancer achievement evaluator.
     */
    @Query(
        """
        SELECT COUNT(ic)
        FROM IssueComment ic
        WHERE ic.issue.id = :issueId
        AND ic.author.id <> :authorId
        """
    )
    long countByIssueIdAndAuthorIdNot(@Param("issueId") Long issueId, @Param("authorId") Long authorId);
}
