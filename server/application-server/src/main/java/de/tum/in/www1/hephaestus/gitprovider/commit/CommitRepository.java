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
 */
@Repository
public interface CommitRepository extends JpaRepository<Commit, Long> {
    /**
     * Find a commit by SHA and repository ID.
     */
    Optional<Commit> findByShaAndRepositoryId(String sha, Long repositoryId);

    /**
     * Check if a commit exists by SHA and repository ID.
     */
    boolean existsByShaAndRepositoryId(String sha, Long repositoryId);

    /**
     * Count commits for a repository.
     */
    long countByRepositoryId(Long repositoryId);

    /**
     * Find the most recent commit for a repository by authored date.
     */
    @Query(
        """
        SELECT c FROM Commit c
        WHERE c.repository.id = :repositoryId
        ORDER BY c.authoredAt DESC
        LIMIT 1
        """
    )
    Optional<Commit> findLatestByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Delete all commits for a repository.
     */
    void deleteByRepositoryId(Long repositoryId);

    /**
     * Upsert a commit by SHA and repository_id.
     * <p>
     * On conflict (same SHA in same repository), updates all mutable fields.
     * Uses {@code COALESCE} for nullable fields so null parameters preserve
     * existing database values (webhooks may provide less data than local git).
     */
    /**
     * Find SHAs of commits with null author_id for a repository.
     */
    @Query(
        value = """
        SELECT gc.sha FROM git_commit gc
        WHERE gc.repository_id = :repositoryId AND gc.author_id IS NULL
        """,
        nativeQuery = true
    )
    List<String> findShasWithNullAuthorByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Find SHAs of commits with null committer_id for a repository.
     */
    @Query(
        value = """
        SELECT gc.sha FROM git_commit gc
        WHERE gc.repository_id = :repositoryId AND gc.committer_id IS NULL
        """,
        nativeQuery = true
    )
    List<String> findShasWithNullCommitterByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Bulk update author_id for commits matching the given SHAs in a repository.
     * Only updates rows where author_id is currently NULL (safe for concurrent use).
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE git_commit SET author_id = :authorId, updated_at = NOW()
        WHERE repository_id = :repositoryId AND author_id IS NULL AND sha IN (:shas)
        """,
        nativeQuery = true
    )
    int bulkUpdateAuthorId(
        @Param("shas") List<String> shas,
        @Param("repositoryId") Long repositoryId,
        @Param("authorId") Long authorId
    );

    /**
     * Bulk update committer_id for commits matching the given SHAs in a repository.
     * Only updates rows where committer_id is currently NULL (safe for concurrent use).
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE git_commit SET committer_id = :committerId, updated_at = NOW()
        WHERE repository_id = :repositoryId AND committer_id IS NULL AND sha IN (:shas)
        """,
        nativeQuery = true
    )
    int bulkUpdateCommitterId(
        @Param("shas") List<String> shas,
        @Param("repositoryId") Long repositoryId,
        @Param("committerId") Long committerId
    );

    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO git_commit (sha, message, message_body, html_url, authored_at, committed_at,
            additions, deletions, changed_files, last_sync_at, created_at, updated_at,
            repository_id, author_id, committer_id)
        VALUES (:sha, :message, :messageBody, :htmlUrl, :authoredAt, :committedAt,
            :additions, :deletions, :changedFiles, :lastSyncAt, NOW(), NOW(),
            :repositoryId, :authorId, :committerId)
        ON CONFLICT (sha, repository_id) DO UPDATE SET
            message = EXCLUDED.message,
            message_body = COALESCE(EXCLUDED.message_body, git_commit.message_body),
            html_url = COALESCE(EXCLUDED.html_url, git_commit.html_url),
            authored_at = EXCLUDED.authored_at,
            committed_at = EXCLUDED.committed_at,
            additions = COALESCE(EXCLUDED.additions, git_commit.additions),
            deletions = COALESCE(EXCLUDED.deletions, git_commit.deletions),
            changed_files = COALESCE(EXCLUDED.changed_files, git_commit.changed_files),
            last_sync_at = EXCLUDED.last_sync_at,
            updated_at = NOW(),
            author_id = COALESCE(EXCLUDED.author_id, git_commit.author_id),
            committer_id = COALESCE(EXCLUDED.committer_id, git_commit.committer_id)
        """,
        nativeQuery = true
    )
    void upsertCommit(
        @Param("sha") String sha,
        @Param("message") String message,
        @Param("messageBody") String messageBody,
        @Param("htmlUrl") String htmlUrl,
        @Param("authoredAt") Instant authoredAt,
        @Param("committedAt") Instant committedAt,
        @Param("additions") Integer additions,
        @Param("deletions") Integer deletions,
        @Param("changedFiles") Integer changedFiles,
        @Param("lastSyncAt") Instant lastSyncAt,
        @Param("repositoryId") Long repositoryId,
        @Param("authorId") Long authorId,
        @Param("committerId") Long committerId
    );
}
