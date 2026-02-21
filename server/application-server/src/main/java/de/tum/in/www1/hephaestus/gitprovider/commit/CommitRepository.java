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
     * Find distinct unresolved author emails for a repository.
     * Returns emails where author_id is NULL but author_email is stored.
     */
    @Query(
        value = """
        SELECT DISTINCT gc.author_email FROM git_commit gc
        WHERE gc.repository_id = :repositoryId AND gc.author_id IS NULL AND gc.author_email IS NOT NULL
        """,
        nativeQuery = true
    )
    List<String> findDistinctUnresolvedAuthorEmailsByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Find distinct unresolved committer emails for a repository.
     * Returns emails where committer_id is NULL but committer_email is stored.
     */
    @Query(
        value = """
        SELECT DISTINCT gc.committer_email FROM git_commit gc
        WHERE gc.repository_id = :repositoryId AND gc.committer_id IS NULL AND gc.committer_email IS NOT NULL
        """,
        nativeQuery = true
    )
    List<String> findDistinctUnresolvedCommitterEmailsByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Bulk update author_id for commits matching a given author email in a repository.
     * Only updates rows where author_id is currently NULL (safe for concurrent use).
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE git_commit SET author_id = :authorId, updated_at = NOW()
        WHERE repository_id = :repositoryId AND author_id IS NULL AND author_email = :email
        """,
        nativeQuery = true
    )
    int bulkUpdateAuthorIdByEmail(
        @Param("email") String email,
        @Param("repositoryId") Long repositoryId,
        @Param("authorId") Long authorId
    );

    /**
     * Bulk update committer_id for commits matching a given committer email in a repository.
     * Only updates rows where committer_id is currently NULL (safe for concurrent use).
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE git_commit SET committer_id = :committerId, updated_at = NOW()
        WHERE repository_id = :repositoryId AND committer_id IS NULL AND committer_email = :email
        """,
        nativeQuery = true
    )
    int bulkUpdateCommitterIdByEmail(
        @Param("email") String email,
        @Param("repositoryId") Long repositoryId,
        @Param("committerId") Long committerId
    );

    /**
     * Find one representative SHA per unresolved email for GraphQL author lookup.
     * Returns pairs of (email, sha) where author_id or committer_id is NULL.
     * Uses DISTINCT ON to pick exactly one SHA per email efficiently.
     */
    @Query(
        value = """
        SELECT sub.email, sub.sha FROM (
            SELECT DISTINCT ON (gc.author_email)
                gc.author_email AS email, gc.sha AS sha
            FROM git_commit gc
            WHERE gc.repository_id = :repositoryId
              AND gc.author_id IS NULL
              AND gc.author_email IS NOT NULL
            UNION ALL
            SELECT DISTINCT ON (gc.committer_email)
                gc.committer_email AS email, gc.sha AS sha
            FROM git_commit gc
            WHERE gc.repository_id = :repositoryId
              AND gc.committer_id IS NULL
              AND gc.committer_email IS NOT NULL
        ) sub
        """,
        nativeQuery = true
    )
    List<Object[]> findRepresentativeShasByUnresolvedEmail(@Param("repositoryId") Long repositoryId);

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
            repository_id, author_id, committer_id, author_email, committer_email)
        VALUES (:sha, :message, :messageBody, :htmlUrl, :authoredAt, :committedAt,
            :additions, :deletions, :changedFiles, :lastSyncAt, NOW(), NOW(),
            :repositoryId, :authorId, :committerId, :authorEmail, :committerEmail)
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
            committer_id = COALESCE(EXCLUDED.committer_id, git_commit.committer_id),
            author_email = COALESCE(EXCLUDED.author_email, git_commit.author_email),
            committer_email = COALESCE(EXCLUDED.committer_email, git_commit.committer_email)
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
        @Param("committerId") Long committerId,
        @Param("authorEmail") String authorEmail,
        @Param("committerEmail") String committerEmail
    );

    /**
     * Find SHAs of commits that have no contributor rows yet.
     * These commits need metadata enrichment (multi-author data + PR links).
     */
    @Query(
        value = """
        SELECT gc.sha FROM git_commit gc
        WHERE gc.repository_id = :repositoryId
          AND NOT EXISTS (
              SELECT 1 FROM commit_contributor cc WHERE cc.commit_id = gc.id
          )
        """,
        nativeQuery = true
    )
    List<String> findShasWithoutContributorsByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Link a commit to pull requests by PR numbers within the same repository.
     * Uses INSERT ... ON CONFLICT DO NOTHING to be idempotent.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        INSERT INTO commit_pull_request (commit_id, pull_request_id)
        SELECT :commitId, i.id
        FROM issue i
        WHERE i.repository_id = :repositoryId
          AND i.number IN (:prNumbers)
          AND i.issue_type = 'PULL_REQUEST'
        ON CONFLICT DO NOTHING
        """,
        nativeQuery = true
    )
    void linkCommitToPullRequests(
        @Param("commitId") Long commitId,
        @Param("repositoryId") Long repositoryId,
        @Param("prNumbers") List<Integer> prNumbers
    );

    /**
     * Bulk-update enrichment metadata fields on a commit.
     * <p>
     * Uses {@code COALESCE} so that NULL parameters preserve existing database values.
     * This ensures webhook-ingested data is not overwritten with NULLs if the
     * GraphQL response omits a field.
     */
    @Modifying
    @Transactional
    @Query(
        value = """
        UPDATE git_commit SET
            additions = COALESCE(:additions, git_commit.additions),
            deletions = COALESCE(:deletions, git_commit.deletions),
            changed_files = COALESCE(:changedFiles, git_commit.changed_files),
            authored_at = COALESCE(:authoredAt, git_commit.authored_at),
            committed_at = COALESCE(:committedAt, git_commit.committed_at),
            message = COALESCE(:message, git_commit.message),
            message_body = COALESCE(:messageBody, git_commit.message_body),
            html_url = COALESCE(:htmlUrl, git_commit.html_url),
            signature_valid = COALESCE(:signatureValid, git_commit.signature_valid),
            authored_by_committer = COALESCE(:authoredByCommitter, git_commit.authored_by_committer),
            committed_via_web = COALESCE(:committedViaWeb, git_commit.committed_via_web),
            parent_count = COALESCE(:parentCount, git_commit.parent_count),
            signature_state = COALESCE(:signatureState, git_commit.signature_state),
            signature_was_signed_by_github = COALESCE(:signatureWasSignedByGitHub, git_commit.signature_was_signed_by_github),
            signature_signer_login = COALESCE(:signatureSignerLogin, git_commit.signature_signer_login),
            parent_shas = COALESCE(:parentShas, git_commit.parent_shas),
            status_check_rollup_state = COALESCE(:statusCheckRollupState, git_commit.status_check_rollup_state),
            on_behalf_of_login = COALESCE(:onBehalfOfLogin, git_commit.on_behalf_of_login),
            updated_at = NOW()
        WHERE git_commit.id = :commitId
        """,
        nativeQuery = true
    )
    void updateEnrichmentMetadata(
        @Param("commitId") Long commitId,
        @Param("additions") Integer additions,
        @Param("deletions") Integer deletions,
        @Param("changedFiles") Integer changedFiles,
        @Param("authoredAt") Instant authoredAt,
        @Param("committedAt") Instant committedAt,
        @Param("message") String message,
        @Param("messageBody") String messageBody,
        @Param("htmlUrl") String htmlUrl,
        @Param("signatureValid") Boolean signatureValid,
        @Param("authoredByCommitter") Boolean authoredByCommitter,
        @Param("committedViaWeb") Boolean committedViaWeb,
        @Param("parentCount") Integer parentCount,
        @Param("signatureState") String signatureState,
        @Param("signatureWasSignedByGitHub") Boolean signatureWasSignedByGitHub,
        @Param("signatureSignerLogin") String signatureSignerLogin,
        @Param("parentShas") String parentShas,
        @Param("statusCheckRollupState") String statusCheckRollupState,
        @Param("onBehalfOfLogin") String onBehalfOfLogin
    );
}
