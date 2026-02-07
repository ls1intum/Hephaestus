package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for PullRequest entities.
 *
 * <p>This repository contains only domain-agnostic queries for the gitprovider domain.
 * Scope-filtered queries (those that join with RepositoryToMonitor or other consuming module
 * entities) belong in the consuming packages (leaderboard, profile, practices, etc.)
 * to maintain clean architecture boundaries.
 *
 * @see de.tum.in.www1.hephaestus.profile.ProfilePullRequestQueryRepository
 */
@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    /**
     * Finds a PR by repository ID and number for sync operations.
     * Repository ID inherently has scope through Organization.
     */
    @Query(
        """
        SELECT p
        FROM PullRequest p
        LEFT JOIN FETCH p.labels
        LEFT JOIN FETCH p.author
        LEFT JOIN FETCH p.assignees
        LEFT JOIN FETCH p.repository
        LEFT JOIN FETCH p.milestone
        WHERE p.repository.id = :repositoryId AND p.number = :number
        """
    )
    Optional<PullRequest> findByRepositoryIdAndNumber(
        @Param("repositoryId") long repositoryId,
        @Param("number") int number
    );

    /**
     * Finds a pull request by ID with assignees eagerly fetched.
     * Useful when assignees need to be accessed outside the original transaction,
     * avoiding LazyInitializationException after the Hibernate session is closed.
     *
     * @param id the pull request ID
     * @return the pull request with assignees loaded, or empty if not found
     */
    @Query(
        """
        SELECT p FROM PullRequest p
        LEFT JOIN FETCH p.assignees
        WHERE p.id = :id
        """
    )
    Optional<PullRequest> findByIdWithAssignees(@Param("id") Long id);

    /**
     * Finds a pull request by ID with repository eagerly fetched.
     * Required for passing PRs across transaction boundaries where the repository
     * relationship needs to be accessed (e.g., for logging nameWithOwner).
     * Avoids LazyInitializationException when PR is fetched in one transaction
     * and repository is accessed in another.
     *
     * @param id the pull request ID
     * @return the pull request with repository loaded, or empty if not found
     */
    @Query(
        """
        SELECT p FROM PullRequest p
        LEFT JOIN FETCH p.repository
        WHERE p.id = :id
        """
    )
    Optional<PullRequest> findByIdWithRepository(@Param("id") Long id);

    /**
     * Finds all pull requests belonging to a repository.
     * Repository ID inherently has scope through Organization.
     *
     * @param repositoryId the repository ID
     * @return list of pull requests for the repository
     */
    List<PullRequest> findAllByRepository_Id(Long repositoryId);

    /**
     * Finds pull requests belonging to a repository with pagination.
     * Uses Slice for efficient batching without requiring a count query.
     * Repository ID inherently has scope through Organization.
     *
     * @param repositoryId the repository ID
     * @param pageable pagination parameters
     * @return slice of pull requests for the repository
     */
    Slice<PullRequest> findByRepository_Id(Long repositoryId, Pageable pageable);

    /**
     * Streams all pull requests belonging to a repository.
     * Repository ID inherently has scope through Organization.
     * <p>
     * Must be used within a try-with-resources block to ensure the stream is closed
     * and the database connection is released. The calling method must be annotated
     * with @Transactional(readOnly = true) for streaming to work properly.
     *
     * @param repositoryId the repository ID
     * @return stream of pull requests for the repository
     */
    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "50"))
    Stream<PullRequest> streamAllByRepository_Id(Long repositoryId);

    /**
     * Atomically inserts or updates a pull request's core fields (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (repository_id, number). This eliminates the race condition where two threads both
     * pass the findById check and try to insert the same pull request.
     * <p>
     * <b>Note:</b> This only handles scalar fields and FK references. ManyToMany relationships
     * (labels, assignees, requestedReviewers) must be handled separately after calling this method.
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO issue (
            id, number, title, body, state, state_reason, html_url, is_locked,
            closed_at, comments_count, last_sync_at, created_at, updated_at,
            author_id, repository_id, milestone_id,
            merged_at, is_draft, is_merged, commits, additions, deletions, changed_files,
            review_decision, merge_state_status, mergeable,
            head_ref_name, base_ref_name, head_ref_oid, base_ref_oid, merged_by_id,
            issue_type
        )
        VALUES (
            :id, :number, :title, :body, :state, :stateReason, :htmlUrl, :isLocked,
            :closedAt, :commentsCount, :lastSyncAt, :createdAt, :updatedAt,
            :authorId, :repositoryId, :milestoneId,
            :mergedAt, :isDraft, :isMerged, :commits, :additions, :deletions, :changedFiles,
            :reviewDecision, :mergeStateStatus, :mergeable,
            :headRefName, :baseRefName, :headRefOid, :baseRefOid, :mergedById,
            'PULL_REQUEST'
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
            merged_at = EXCLUDED.merged_at,
            is_draft = EXCLUDED.is_draft,
            is_merged = EXCLUDED.is_merged,
            commits = EXCLUDED.commits,
            additions = EXCLUDED.additions,
            deletions = EXCLUDED.deletions,
            changed_files = EXCLUDED.changed_files,
            review_decision = COALESCE(EXCLUDED.review_decision, issue.review_decision),
            merge_state_status = COALESCE(EXCLUDED.merge_state_status, issue.merge_state_status),
            mergeable = COALESCE(EXCLUDED.mergeable, issue.mergeable),
            head_ref_name = EXCLUDED.head_ref_name,
            base_ref_name = EXCLUDED.base_ref_name,
            head_ref_oid = EXCLUDED.head_ref_oid,
            base_ref_oid = EXCLUDED.base_ref_oid,
            merged_by_id = COALESCE(EXCLUDED.merged_by_id, issue.merged_by_id),
            issue_type = EXCLUDED.issue_type
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("id") Long id,
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
        @Param("mergedAt") Instant mergedAt,
        @Param("isDraft") boolean isDraft,
        @Param("isMerged") boolean isMerged,
        @Param("commits") int commits,
        @Param("additions") int additions,
        @Param("deletions") int deletions,
        @Param("changedFiles") int changedFiles,
        @Param("reviewDecision") String reviewDecision,
        @Param("mergeStateStatus") String mergeStateStatus,
        @Param("mergeable") Boolean mergeable,
        @Param("headRefName") String headRefName,
        @Param("baseRefName") String baseRefName,
        @Param("headRefOid") String headRefOid,
        @Param("baseRefOid") String baseRefOid,
        @Param("mergedById") Long mergedById
    );
}
