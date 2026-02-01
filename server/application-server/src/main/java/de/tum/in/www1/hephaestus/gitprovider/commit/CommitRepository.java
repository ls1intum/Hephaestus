package de.tum.in.www1.hephaestus.gitprovider.commit;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Repository for Commit entities.
 * <p>
 * Note: Commit uses auto-generated Long ID as primary key.
 */
@Repository
public interface CommitRepository extends JpaRepository<Commit, Long> {
    /**
     * Find a commit by SHA and repository ID.
     * This is the primary lookup method since SHAs are unique per repository.
     */
    Optional<Commit> findByShaAndRepositoryId(String sha, Long repositoryId);

    /**
     * Find all commits for a repository, ordered by committed date descending.
     */
    @Query("SELECT c FROM Commit c WHERE c.repository.id = :repositoryId ORDER BY c.committedAt DESC")
    List<Commit> findByRepositoryIdOrderByCommittedAtDesc(@Param("repositoryId") Long repositoryId);

    /**
     * Find commits by author ID.
     */
    List<Commit> findByAuthorId(Long authorId);

    /**
     * Check if a commit exists by SHA and repository ID.
     */
    boolean existsByShaAndRepositoryId(String sha, Long repositoryId);

    /**
     * Count commits for a repository.
     */
    long countByRepositoryId(Long repositoryId);

    /**
     * Atomically inserts or updates a commit's core fields (race-condition safe).
     * <p>
     * Uses PostgreSQL's ON CONFLICT to handle concurrent inserts on the unique constraint
     * (sha, repository_id). This eliminates the race condition where two threads both
     * pass the findByShaAndRepositoryId check and try to insert the same commit.
     * <p>
     * Note: Since commit uses auto-generated ID, we don't pass the ID - it's generated
     * on insert and preserved on update via DO UPDATE.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Transactional
    @Query(
        value = """
        INSERT INTO commit (sha, repository_id, message, message_body, html_url,
            authored_at, committed_at, additions, deletions, changed_files, last_sync_at,
            author_id, committer_id)
        VALUES (:sha, :repositoryId, :message, :messageBody, :htmlUrl,
            :authoredAt, :committedAt, :additions, :deletions, :changedFiles, :lastSyncAt,
            :authorId, :committerId)
        ON CONFLICT (sha, repository_id) DO UPDATE SET
            message = EXCLUDED.message,
            message_body = EXCLUDED.message_body,
            html_url = EXCLUDED.html_url,
            authored_at = EXCLUDED.authored_at,
            committed_at = EXCLUDED.committed_at,
            additions = EXCLUDED.additions,
            deletions = EXCLUDED.deletions,
            changed_files = EXCLUDED.changed_files,
            last_sync_at = EXCLUDED.last_sync_at,
            author_id = COALESCE(EXCLUDED.author_id, commit.author_id),
            committer_id = COALESCE(EXCLUDED.committer_id, commit.committer_id)
        """,
        nativeQuery = true
    )
    void upsertCore(
        @Param("sha") String sha,
        @Param("repositoryId") Long repositoryId,
        @Param("message") String message,
        @Param("messageBody") String messageBody,
        @Param("htmlUrl") String htmlUrl,
        @Param("authoredAt") Instant authoredAt,
        @Param("committedAt") Instant committedAt,
        @Param("additions") int additions,
        @Param("deletions") int deletions,
        @Param("changedFiles") int changedFiles,
        @Param("lastSyncAt") Instant lastSyncAt,
        @Param("authorId") Long authorId,
        @Param("committerId") Long committerId
    );
}
