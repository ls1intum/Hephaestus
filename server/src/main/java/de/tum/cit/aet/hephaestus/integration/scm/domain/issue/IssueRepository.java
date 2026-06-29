package de.tum.cit.aet.hephaestus.integration.scm.domain.issue;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
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
 * All queries filter by repository ID, which inherently carries scope
 * through the repository -> workspace relationship chain.
 */
@WorkspaceAgnostic("Issues scoped through repository_id -> repository.workspace_id")
public interface IssueRepository extends JpaRepository<Issue, Long> {
    /**
     * Finds an issue (not a pull request) by repository ID and number.
     * Uses {@code TYPE(i) = Issue} to exclude PullRequest subclass rows, which is
     * necessary for GitLab where issues and merge requests have separate IID
     * namespaces (Issue #5 and MR !5 can coexist in the same project).
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
        WHERE TYPE(i) = Issue AND i.repository.id = :repositoryId AND i.number = :number
        """
    )
    Optional<Issue> findByRepositoryIdAndNumber(@Param("repositoryId") long repositoryId, @Param("number") int number);

    /** Fetches an issue with its repository eagerly — used to build an issue-detection job submission. */
    @Query("SELECT i FROM Issue i LEFT JOIN FETCH i.repository WHERE TYPE(i) = Issue AND i.id = :id")
    Optional<Issue> findByIdWithRepository(@Param("id") long id);

    /**
     * Fetches an issue with its author eagerly — used by the practice-detection delivery path to resolve
     * the about-user in the same query (mirrors {@code PullRequestRepository.findByIdWithAuthor}, avoiding
     * a lazy-load round-trip on {@code issue.getAuthor()}). Restricted to {@code TYPE(i) = Issue}.
     */
    @Query("SELECT i FROM Issue i LEFT JOIN FETCH i.author WHERE TYPE(i) = Issue AND i.id = :id")
    Optional<Issue> findByIdWithAuthor(@Param("id") long id);

    /**
     * Fetches an issue with the associations {@code PracticeReviewDetectionGate.evaluateIssue} needs:
     * repository (workspace resolution) and assignees (role check). Restricted to {@code TYPE(i) = Issue}
     * so a pull-request row never enters the issue-detection path.
     */
    @Query(
        "SELECT i FROM Issue i LEFT JOIN FETCH i.repository LEFT JOIN FETCH i.assignees " +
            "WHERE TYPE(i) = Issue AND i.id = :id"
    )
    Optional<Issue> findByIdWithRepositoryAndAssignees(@Param("id") long id);

    List<Issue> findAllByRepository_Id(Long repositoryId);

    /** Slice (rather than Page) so batching needs no count query. */
    Slice<Issue> findByRepository_Id(Long repositoryId, Pageable pageable);

    /**
     * Repository-wide issue inventory (pure issues, PullRequest subclass rows excluded) ordered
     * newest-first by number, for the cross-artifact project-context telescope. The author is fetched
     * up front to avoid a per-row lazy load; labels/comments/bodies are intentionally NOT fetched — the
     * inventory is a compact "what else exists in this project" index, not a full body.
     *
     * @param repositoryId the repository ID
     * @param pageable the cap (newest N) — caller supplies {@code PageRequest.of(0, cap)}
     * @return newest-first issues for the repository
     */
    @Query(
        "SELECT i FROM Issue i LEFT JOIN FETCH i.author LEFT JOIN FETCH i.milestone " +
            "WHERE TYPE(i) = Issue AND i.repository.id = :repositoryId ORDER BY i.number DESC"
    )
    List<Issue> findIssueInventoryByRepositoryId(@Param("repositoryId") long repositoryId, Pageable pageable);

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

    /**
     * Promotes a stub {@code ISSUE} row to {@code PULL_REQUEST} ahead of the upsert. The
     * {@code COALESCE}s seed PR-specific NOT-NULL primitives so the discriminator flip survives
     * the single-table-inheritance CHECK constraint; {@code upsertCore} overwrites them with
     * real values immediately after.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = "UPDATE issue SET issue_type = :newType, " +
            "is_draft = COALESCE(is_draft, false), " +
            "is_merged = COALESCE(is_merged, false), " +
            "commits = COALESCE(commits, 0), " +
            "additions = COALESCE(additions, 0), " +
            "deletions = COALESCE(deletions, 0), " +
            "changed_files = COALESCE(changed_files, 0) " +
            "WHERE repository_id = :repositoryId AND number = :number AND issue_type = :currentType",
        nativeQuery = true
    )
    int correctDiscriminator(
        @Param("repositoryId") long repositoryId,
        @Param("number") int number,
        @Param("currentType") String currentType,
        @Param("newType") String newType
    );

    /**
     * Atomically inserts or updates an issue's core fields (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (repository_id, issue_type, number). This eliminates the race condition where two threads
     * both pass the findById check and try to insert the same issue.
     * <p>
     * <b>Note:</b> This only handles scalar fields and FK references. ManyToMany relationships
     * (labels, assignees) must be handled separately after calling this method.
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
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
            :nativeId, :providerId, :number, :title, :body, :state, :stateReason, :htmlUrl,
            COALESCE(:isLocked, false),
            :closedAt, COALESCE(:commentsCount, 0), :lastSyncAt, :createdAt, :updatedAt,
            :authorId, :repositoryId, :milestoneId, :issueTypeId,
            :parentIssueId, :subIssuesTotal, :subIssuesCompleted, :subIssuesPercentCompleted,
            'ISSUE'
        )
        ON CONFLICT (repository_id, issue_type, number) DO UPDATE SET
            title = EXCLUDED.title,
            body = EXCLUDED.body,
            state = EXCLUDED.state,
            state_reason = EXCLUDED.state_reason,
            html_url = EXCLUDED.html_url,
            is_locked = COALESCE(EXCLUDED.is_locked, issue.is_locked),
            closed_at = EXCLUDED.closed_at,
            comments_count = COALESCE(EXCLUDED.comments_count, issue.comments_count),
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
        @Param("isLocked") Boolean isLocked,
        @Param("closedAt") Instant closedAt,
        @Param("commentsCount") Integer commentsCount,
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
