package de.tum.in.www1.hephaestus.gitprovider.commit;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

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
    @Query("""
        SELECT c FROM Commit c
        WHERE c.repository.id = :repositoryId
        ORDER BY c.authoredAt DESC
        LIMIT 1
        """)
    Optional<Commit> findLatestByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Delete all commits for a repository.
     */
    void deleteByRepositoryId(Long repositoryId);
}
