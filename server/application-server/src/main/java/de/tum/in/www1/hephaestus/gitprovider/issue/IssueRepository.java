package de.tum.in.www1.hephaestus.gitprovider.issue;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

/**
 * Repository for issue entities.
 *
 * <p>All queries filter by repository ID which inherently carries workspace scope
 * through the Repository -> Organization -> Workspace.organization chain.
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
     * Finds all issues belonging to a repository with comments eagerly fetched.
     * Used by backfill operations to avoid LazyInitializationException when accessing
     * comments after EntityManager.clear().
     *
     * @param repositoryId the repository ID
     * @return list of issues with comments for the repository
     */
    @Query(
        """
        SELECT DISTINCT i
        FROM Issue i
        LEFT JOIN FETCH i.comments c
        LEFT JOIN FETCH c.author
        LEFT JOIN FETCH i.repository
        WHERE i.repository.id = :repositoryId
        """
    )
    List<Issue> findAllByRepositoryIdWithComments(@Param("repositoryId") Long repositoryId);
}
