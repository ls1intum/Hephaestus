package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import jakarta.persistence.QueryHint;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Stream;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    /**
     * Finds the earliest contribution date for a user across all workspaces.
     * Used for global contributor statistics display.
     */
    @WorkspaceAgnostic("Global query for contributor page - shows first contribution across all workspaces")
    @Query(
        """
        SELECT MIN(p.createdAt)
        FROM PullRequest p
        WHERE p.author.login ILIKE :authorLogin
        """
    )
    Optional<Instant> firstContributionByAuthorLogin(@Param("authorLogin") String authorLogin);

    @Transactional
    @Query(
        """
        SELECT p
        FROM PullRequest p
        LEFT JOIN FETCH p.labels
        JOIN FETCH p.author
        LEFT JOIN FETCH p.assignees
        LEFT JOIN FETCH p.repository
        WHERE (p.author.login ILIKE :assigneeLogin OR LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM p.assignees u))
            AND p.state IN :states
            AND p.repository.organization.workspaceId = :workspaceId
        ORDER BY p.createdAt DESC
        """
    )
    List<PullRequest> findAssignedByLoginAndStates(
        @Param("assigneeLogin") String assigneeLogin,
        @Param("states") Set<PullRequest.State> states,
        @Param("workspaceId") Long workspaceId
    );

    @Query(
        """
        SELECT p
        FROM PullRequest p
        LEFT JOIN FETCH p.labels
        JOIN FETCH p.author
        LEFT JOIN FETCH p.assignees
        LEFT JOIN FETCH p.repository
        WHERE (p.author.login ILIKE :assigneeLogin OR LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM p.assignees u))
            AND p.state IN :states
            AND p.updatedAt >= :activitySince
            AND p.repository.organization.workspaceId = :workspaceId
        ORDER BY p.createdAt DESC
        """
    )
    List<PullRequest> findAssignedByLoginAndStatesUpdatedSince(
        @Param("assigneeLogin") String assigneeLogin,
        @Param("states") Set<PullRequest.State> states,
        @Param("activitySince") Instant activitySince,
        @Param("workspaceId") Long workspaceId
    );

    /**
     * Finds a PR by repository ID and number for sync operations.
     * Repository ID inherently has workspace through organization.workspaceId.
     */
    @WorkspaceAgnostic("Sync operation - Repository ID has workspace through organization")
    @Query(
        """
        SELECT p
        FROM PullRequest p
        LEFT JOIN FETCH p.labels
        LEFT JOIN FETCH p.author
        LEFT JOIN FETCH p.assignees
        LEFT JOIN FETCH p.repository
        WHERE p.repository.id = :repositoryId AND p.number = :number
        """
    )
    Optional<PullRequest> findByRepositoryIdAndNumber(
        @Param("repositoryId") long repositoryId,
        @Param("number") int number
    );

    /**
     * Finds all synced PR numbers for a repository during sync operations.
     * Repository ID inherently has workspace through organization.workspaceId.
     */
    @WorkspaceAgnostic("Sync operation - Repository ID has workspace through organization")
    @Query(
        """
        SELECT p.number
        FROM PullRequest p
        WHERE Type(p) = PullRequest
        AND p.repository.id = :repositoryId
        AND p.lastSyncAt IS NOT NULL
        """
    )
    Set<Integer> findAllSyncedPullRequestNumbers(@Param("repositoryId") long repositoryId);

    /**
     * Finds all pull requests belonging to a repository.
     * Repository ID inherently has workspace through organization.workspaceId.
     *
     * @param repositoryId the repository ID
     * @return list of pull requests for the repository
     */
    @WorkspaceAgnostic("Sync operation - Repository ID has workspace through organization")
    List<PullRequest> findAllByRepository_Id(Long repositoryId);

    /**
     * Streams all pull requests belonging to a repository.
     * Repository ID inherently has workspace through organization.workspaceId.
     * <p>
     * Must be used within a try-with-resources block to ensure the stream is closed
     * and the database connection is released. The calling method must be annotated
     * with @Transactional(readOnly = true) for streaming to work properly.
     *
     * @param repositoryId the repository ID
     * @return stream of pull requests for the repository
     */
    @WorkspaceAgnostic("Sync operation - Repository ID has workspace through organization")
    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "50"))
    Stream<PullRequest> streamAllByRepository_Id(Long repositoryId);
}
