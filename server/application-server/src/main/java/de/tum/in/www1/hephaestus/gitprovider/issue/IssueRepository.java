package de.tum.in.www1.hephaestus.gitprovider.issue;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for issue entities.
 *
 * <p>All queries filter by repository ID which inherently carries scope
 * through the Repository -> Organization relationship chain.
 */
public interface IssueRepository extends JpaRepository<Issue, Long> {
    /**
     * Finds an issue by repository ID and number.
     * This is the canonical lookup for sync operations as it uses the natural key
     * (repository_id, number) which is consistent across both GraphQL sync and
     * webhook events, regardless of which ID format they use.
     *
     * @param repositoryId the repository ID
     * @param number the issue number within the repository
     * @return the issue if found
     */
    @Query(
        """
        SELECT i
        FROM Issue i
        LEFT JOIN FETCH i.labels
        LEFT JOIN FETCH i.author
        LEFT JOIN FETCH i.assignees
        LEFT JOIN FETCH i.repository
        LEFT JOIN FETCH i.milestone
        WHERE i.repository.id = :repositoryId AND i.number = :number
        """
    )
    Optional<Issue> findByRepositoryIdAndNumber(@Param("repositoryId") long repositoryId, @Param("number") int number);

    /**
     * Finds all issues belonging to a repository.
     *
     * @param repositoryId the repository ID
     * @return list of issues for the repository
     */
    List<Issue> findAllByRepository_Id(Long repositoryId);

    /**
     * Finds issues belonging to a repository with pagination.
     * Uses Slice for efficient batching without requiring a count query.
     *
     * @param repositoryId the repository ID
     * @param pageable pagination parameters
     * @return slice of issues for the repository
     */
    Slice<Issue> findByRepository_Id(Long repositoryId, Pageable pageable);

    /**
     * Nullifies milestone references on all issues that reference the given milestone.
     * <p>
     * This is a direct database update that doesn't rely on Hibernate's collection state.
     * MUST be called before deleting a milestone to avoid foreign key constraint violations.
     * <p>
     * Unlike {@code milestone.removeAllIssues()} which operates on the in-memory collection
     * (which may be stale or not fully loaded), this method updates ALL issues in the
     * database that reference the milestone.
     *
     * @param milestoneId the milestone ID to detach from issues
     * @return the number of issues updated
     */
    @Modifying
    @Query("UPDATE Issue i SET i.milestone = null WHERE i.milestone.id = :milestoneId")
    int clearMilestoneReferences(@Param("milestoneId") Long milestoneId);

    /**
     * Atomically inserts or updates an issue's core fields (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (repository_id, number). This eliminates the race condition where two threads both
     * pass the findById check and try to insert the same issue.
     * <p>
     * <b>Note:</b> This only handles scalar fields and FK references. ManyToMany relationships
     * (labels, assignees) must be handled separately after calling this method.
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
    /**
     * Finds an issue by ID with its blockedBy collection eagerly loaded.
     * <p>
     * This is needed by dependency sync because the persistence context may have been
     * cleared by prior {@code upsertCore()} calls (via {@code clearAutomatically = true}),
     * which detaches entities and invalidates lazy proxies.
     *
     * @param id the issue ID
     * @return the issue with blockedBy eagerly loaded, if found
     */
    @Query(
        """
        SELECT i
        FROM Issue i
        LEFT JOIN FETCH i.blockedBy
        WHERE i.id = :id
        """
    )
    Optional<Issue> findByIdWithBlockedBy(@Param("id") Long id);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO issue (
            native_id, provider_id, number, title, body, state, state_reason, html_url, is_locked,
            closed_at, comments_count, last_sync_at, created_at, updated_at,
            author_id, repository_id, milestone_id, issue_type_id,
            parent_issue_id, sub_issues_total, sub_issues_completed, sub_issues_percent_completed,
            issue_type
        )
        VALUES (
            :nativeId, :providerId, :number, :title, :body, :state, :stateReason, :htmlUrl, :isLocked,
            :closedAt, :commentsCount, :lastSyncAt, :createdAt, :updatedAt,
            :authorId, :repositoryId, :milestoneId, :issueTypeId,
            :parentIssueId, :subIssuesTotal, :subIssuesCompleted, :subIssuesPercentCompleted,
            'ISSUE'
        )
        ON CONFLICT (repository_id, number) DO UPDATE SET
            title = EXCLUDED.title,
            body = EXCLUDED.body,
            state = EXCLUDED.state,
            state_reason = EXCLUDED.state_reason,
            html_url = EXCLUDED.html_url,
            is_locked = EXCLUDED.is_locked,
            closed_at = EXCLUDED.closed_at,
            comments_count = EXCLUDED.comments_count,
            last_sync_at = EXCLUDED.last_sync_at,
            updated_at = EXCLUDED.updated_at,
            author_id = COALESCE(EXCLUDED.author_id, issue.author_id),
            milestone_id = EXCLUDED.milestone_id,
            issue_type_id = COALESCE(EXCLUDED.issue_type_id, issue.issue_type_id),
            parent_issue_id = COALESCE(EXCLUDED.parent_issue_id, issue.parent_issue_id),
            sub_issues_total = COALESCE(EXCLUDED.sub_issues_total, issue.sub_issues_total),
            sub_issues_completed = COALESCE(EXCLUDED.sub_issues_completed, issue.sub_issues_completed),
            sub_issues_percent_completed = COALESCE(EXCLUDED.sub_issues_percent_completed, issue.sub_issues_percent_completed)
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("nativeId") Long nativeId,
        @Param("providerId") Long providerId,
        @Param("number") int number,
        @Param("title") String title,
        @Param("body") String body,
        @Param("state") String state,
        @Param("stateReason") String stateReason,
        @Param("htmlUrl") String htmlUrl,
        @Param("isLocked") boolean isLocked,
        @Param("closedAt") Instant closedAt,
        @Param("commentsCount") int commentsCount,
        @Param("lastSyncAt") Instant lastSyncAt,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt,
        @Param("authorId") Long authorId,
        @Param("repositoryId") Long repositoryId,
        @Param("milestoneId") Long milestoneId,
        @Param("issueTypeId") String issueTypeId,
        @Param("parentIssueId") Long parentIssueId,
        @Param("subIssuesTotal") Integer subIssuesTotal,
        @Param("subIssuesCompleted") Integer subIssuesCompleted,
        @Param("subIssuesPercentCompleted") Integer subIssuesPercentCompleted
    );
}
