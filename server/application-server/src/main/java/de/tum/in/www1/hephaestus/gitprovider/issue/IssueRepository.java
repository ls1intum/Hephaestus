package de.tum.in.www1.hephaestus.gitprovider.issue;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * Repository for issue entities.
 *
 * <p>All queries filter by repository ID which inherently carries scope
 * through the Repository -> Organization relationship chain.
 */
public interface IssueRepository extends JpaRepository<Issue, Long> {
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
     * Streams all issues belonging to a repository.
     * <p>
     * Must be used within a try-with-resources block to ensure the stream is closed
     * and the database connection is released. The calling method must be annotated
     * with @Transactional(readOnly = true) for streaming to work properly.
     *
     * @param repositoryId the repository ID
     * @return stream of issues for the repository
     */
    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "50"))
    Stream<Issue> streamAllByRepository_Id(Long repositoryId);

    @Query(
        """
        SELECT i.number
        FROM Issue i
        WHERE Type(i) = Issue
        AND i.repository.id = :repositoryId
        AND i.lastSyncAt IS NOT NULL
        """
    )
    Set<Integer> findAllSyncedIssueNumbers(@Param("repositoryId") long repositoryId);

    /**
     * Finds issues belonging to a repository with comments eagerly fetched,
     * with pagination support.
     *
     * @param repositoryId the repository ID
     * @param pageable pagination parameters
     * @return slice of issues with comments for the repository
     */
    @Query(
        """
        SELECT DISTINCT i
        FROM Issue i
        LEFT JOIN FETCH i.comments c
        LEFT JOIN FETCH c.author
        LEFT JOIN FETCH i.repository
        WHERE i.repository.id = :repositoryId
        ORDER BY i.id
        """
    )
    Slice<Issue> findByRepositoryIdWithComments(@Param("repositoryId") Long repositoryId, Pageable pageable);

    /**
     * Counts the number of issues (excluding pull requests) in a repository.
     * Used for progress estimation without loading entities into memory.
     *
     * @param repositoryId the repository ID
     * @return count of issues
     */
    @Query("SELECT COUNT(i) FROM Issue i WHERE TYPE(i) = Issue AND i.repository.id = :repositoryId")
    long countIssuesByRepositoryId(@Param("repositoryId") Long repositoryId);

    /**
     * Counts the total number of comments across all issues in a repository.
     * Used for progress estimation without loading entities into memory.
     *
     * @param repositoryId the repository ID
     * @return count of issue comments
     */
    @Query(
        """
        SELECT COUNT(c)
        FROM Issue i
        JOIN i.comments c
        WHERE i.repository.id = :repositoryId
        """
    )
    long countCommentsByRepositoryId(@Param("repositoryId") Long repositoryId);
}
