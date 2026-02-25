package de.tum.in.www1.hephaestus.gitprovider.discussion;

import java.time.Instant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Discussion entities.
 */
@Repository
public interface DiscussionRepository extends JpaRepository<Discussion, Long> {
    /**
     * Find a discussion by repository ID and discussion number.
     * Fetches labels and other necessary associations for post-upsert relationship updates.
     *
     * @param repositoryId the repository ID
     * @param number       the discussion number within the repository
     * @return the discussion if found
     */
    @Query(
        """
        SELECT d
        FROM Discussion d
        LEFT JOIN FETCH d.labels
        LEFT JOIN FETCH d.author
        LEFT JOIN FETCH d.repository
        LEFT JOIN FETCH d.category
        WHERE d.repository.id = :repositoryId AND d.number = :number
        """
    )
    Optional<Discussion> findByRepositoryIdAndNumber(
        @Param("repositoryId") Long repositoryId,
        @Param("number") int number
    );

    /**
     * Check if a discussion exists by repository ID and number.
     */
    boolean existsByRepositoryIdAndNumber(Long repositoryId, int number);

    /**
     * Atomically inserts or updates a discussion's core fields (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (repository_id, number). This eliminates the race condition where two threads both
     * pass the findById check and try to insert the same discussion.
     * <p>
     * <b>Note:</b> This only handles scalar fields and FK references. ManyToMany relationships
     * (labels) must be handled separately after calling this method.
     *
     * @return 1 if inserted, 1 if updated (always 1 on success due to DO UPDATE)
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO discussion (
            id, repository_id, number, title, body, html_url, state, state_reason,
            is_locked, active_lock_reason, closed_at, answer_chosen_at, comment_count,
            upvote_count, last_sync_at, created_at, updated_at, author_id, category_id, answer_chosen_by_id
        )
        VALUES (
            :id, :repositoryId, :number, :title, :body, :htmlUrl, :state, :stateReason,
            :isLocked, :activeLockReason, :closedAt, :answerChosenAt, :commentCount,
            :upvoteCount, :lastSyncAt, :createdAt, :updatedAt, :authorId, :categoryId, :answerChosenById
        )
        ON CONFLICT (repository_id, number) DO UPDATE SET
            title = EXCLUDED.title,
            body = EXCLUDED.body,
            html_url = EXCLUDED.html_url,
            state = EXCLUDED.state,
            state_reason = EXCLUDED.state_reason,
            is_locked = EXCLUDED.is_locked,
            active_lock_reason = EXCLUDED.active_lock_reason,
            closed_at = EXCLUDED.closed_at,
            answer_chosen_at = EXCLUDED.answer_chosen_at,
            comment_count = EXCLUDED.comment_count,
            upvote_count = EXCLUDED.upvote_count,
            last_sync_at = EXCLUDED.last_sync_at,
            updated_at = EXCLUDED.updated_at,
            author_id = COALESCE(EXCLUDED.author_id, discussion.author_id),
            category_id = EXCLUDED.category_id,
            answer_chosen_by_id = EXCLUDED.answer_chosen_by_id
        """,
        nativeQuery = true
    )
    int upsertCore(
        @Param("id") Long id,
        @Param("repositoryId") Long repositoryId,
        @Param("number") int number,
        @Param("title") String title,
        @Param("body") String body,
        @Param("htmlUrl") String htmlUrl,
        @Param("state") String state,
        @Param("stateReason") String stateReason,
        @Param("isLocked") boolean isLocked,
        @Param("activeLockReason") String activeLockReason,
        @Param("closedAt") Instant closedAt,
        @Param("answerChosenAt") Instant answerChosenAt,
        @Param("commentCount") int commentCount,
        @Param("upvoteCount") int upvoteCount,
        @Param("lastSyncAt") Instant lastSyncAt,
        @Param("createdAt") Instant createdAt,
        @Param("updatedAt") Instant updatedAt,
        @Param("authorId") Long authorId,
        @Param("categoryId") String categoryId,
        @Param("answerChosenById") Long answerChosenById
    );
}
