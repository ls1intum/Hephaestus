package de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.common.RepositoryItemCountProjection;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
@WorkspaceAgnostic("Comments scoped through issue_id -> repository.workspace_id")
public interface IssueCommentRepository extends JpaRepository<IssueComment, Long> {
    Optional<IssueComment> findByNativeIdAndProviderId(Long nativeId, Long providerId);

    /**
     * Per-repository comment count for the sync-observability breakdown, batched over every repository
     * of a connection in one grouped join. Counts comments on pull requests as well as on pure issues —
     * both are {@code Issue} rows under single-table inheritance, and the sync path that fetches them is
     * the same one, so splitting them here would imply a distinction the sync doesn't make.
     *
     * <p>Comments of a tombstoned parent ({@code c.issue.deletedAt IS NOT NULL}) are excluded, matching
     * how the issue and pull-request counts already drop tombstoned rows. A comment has no tombstone of
     * its own: it goes away with the issue it hangs off, so the parent's tombstone is the only signal
     * there is. Counting them would reintroduce on the child row exactly the permanent inflation the
     * deletion sweep removes from the parent — the admin would see an issue count fall while its
     * comment count stayed put.
     *
     * <p>The predicate rides the {@code c.issue} join that the grouping already needs, so this stays one
     * grouped query for the whole connection.
     */
    @Query(
        "SELECT c.issue.repository.id AS repositoryId, COUNT(c) AS itemCount FROM IssueComment c " +
            "WHERE c.issue.repository.id IN :repositoryIds AND c.issue.deletedAt IS NULL " +
            "GROUP BY c.issue.repository.id"
    )
    List<RepositoryItemCountProjection> countGroupedByRepositoryIds(
        @Param("repositoryIds") Collection<Long> repositoryIds
    );

    /**
     * Batch fetch comments by id with author, issue and repository eagerly loaded (one query, no N+1).
     * Used by the profile module to hydrate ActivityEvent target entities.
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
            AND (:onlyFromPullRequests = false OR TYPE(i) = PullRequest)
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

    /**
     * All general (conversation-tab) comments on an issue or merge request, author eagerly fetched,
     * oldest-first.
     *
     * <p>Used by {@code GeneralReviewCommentContentSource} to materialise the non-positioned MR
     * review discussion that {@code GitLabDiscussionSyncService} routes to {@link IssueComment} (any
     * note without a diff position). The inline-only {@code comments.json} cannot see these, so the
     * reviewer-craft practices were blind to review that happened in the conversation tab.
     *
     * <p>This fetch is intentionally unbounded: the consumer first filters out blank and bot-authored
     * notes, then tail-slices to {@code GeneralReviewCommentContentSource.MAX_COMMENTS}. Pushing a DB-side
     * {@code LIMIT} here would slice the raw set (bot/blank rows included) and could drop the latest real
     * approval, so the cap stays consumer-side. Conversation-tab comment counts are small in practice.
     *
     * @param issueId the issue/PR id (a {@code PullRequest} is an {@code Issue} subtype, so its
     *     general notes are {@code IssueComment} rows keyed by the same id)
     * @return general comments ordered by creation time ascending
     */
    @Query(
        """
        SELECT ic
        FROM IssueComment ic
        LEFT JOIN FETCH ic.author
        WHERE ic.issue.id = :issueId
        ORDER BY ic.createdAt ASC
        """
    )
    List<IssueComment> findByIssueIdWithAuthorOrderByCreatedAt(@Param("issueId") Long issueId);
}
