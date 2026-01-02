package de.tum.in.www1.hephaestus.gitprovider.commit;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for GitCommit entities.
 */
@Repository
public interface GitCommitRepository extends JpaRepository<GitCommit, String> {
    /**
     * Find a commit by its SHA.
     */
    Optional<GitCommit> findBySha(String sha);

    /**
     * Find a commit by repository ID and SHA.
     */
    Optional<GitCommit> findByRepositoryIdAndSha(Long repositoryId, String sha);

    /**
     * Find commits by repository ID.
     */
    List<GitCommit> findByRepositoryId(Long repositoryId);

    /**
     * Find commits by author's user ID.
     */
    List<GitCommit> findByAuthorId(Long authorId);

    /**
     * Find commits that are missing statistics (need enrichment).
     */
    @Query("SELECT c FROM GitCommit c WHERE c.repository.id = :repositoryId AND c.additions IS NULL")
    List<GitCommit> findCommitsMissingStatistics(@Param("repositoryId") Long repositoryId);

    /**
     * Check if a commit exists by SHA.
     */
    boolean existsBySha(String sha);
}
