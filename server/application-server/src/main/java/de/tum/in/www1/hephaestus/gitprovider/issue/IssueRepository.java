package de.tum.in.www1.hephaestus.gitprovider.issue;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
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
 * <p>Workspace-agnostic: Issues are scoped through their repository which has
 * workspace context through {@code repository.organization.workspaceId}.
 * All queries filter by repository ID which inherently carries workspace scope.
 */
@WorkspaceAgnostic("Scoped through repository.organization.workspaceId")
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

    @Query(
        """
        SELECT i.number
        FROM Issue i
        WHERE Type(i) = Issue
        AND i.repository.id = :repositoryId
        AND i.hasPullRequest = TRUE
        """
    )
    Set<Integer> findAllIssueNumbersWithPullRequest(@Param("repositoryId") long repositoryId);
}
