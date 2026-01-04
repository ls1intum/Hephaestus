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
        LEFT JOIN FETCH p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE (p.author.login ILIKE :assigneeLogin OR LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM p.assignees u))
            AND p.state IN :states
            AND rtm.workspace.id = :workspaceId
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
        LEFT JOIN FETCH p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE (p.author.login ILIKE :assigneeLogin OR LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM p.assignees u))
            AND p.state IN :states
            AND p.updatedAt >= :activitySince
            AND rtm.workspace.id = :workspaceId
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
     * Finds a pull request by ID with assignees eagerly fetched.
     * Used by BadPracticeEventListener to avoid LazyInitializationException
     * when assignees are accessed after the Hibernate session is closed.
     *
     * @param id the pull request ID
     * @return the pull request with assignees loaded, or empty if not found
     */
    @WorkspaceAgnostic("Event processing - PR ID is known from domain event")
    @Query(
        """
        SELECT p FROM PullRequest p
        LEFT JOIN FETCH p.assignees
        WHERE p.id = :id
        """
    )
    Optional<PullRequest> findByIdWithAssignees(@Param("id") Long id);

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

    /**
     * Finds all pull requests belonging to a repository with reviews and their comments eagerly fetched.
     * Used by backfill operations to avoid LazyInitializationException when accessing
     * reviews and their comments after EntityManager.clear().
     *
     * <p>The XP calculator needs review.comments.size() for bonus calculation, so we must
     * fetch both the reviews and their comments in the same query.
     *
     * @param repositoryId the repository ID
     * @return list of pull requests with reviews and comments for the repository
     */
    @WorkspaceAgnostic("Backfill operation - Repository ID has workspace through organization")
    @Query(
        """
        SELECT DISTINCT p
        FROM PullRequest p
        LEFT JOIN FETCH p.reviews r
        LEFT JOIN FETCH r.comments
        LEFT JOIN FETCH p.repository
        WHERE p.repository.id = :repositoryId
        """
    )
    List<PullRequest> findAllByRepositoryIdWithReviews(@Param("repositoryId") Long repositoryId);

    /**
     * Finds all pull requests belonging to a repository with review comments eagerly fetched.
     * Used by backfill operations to avoid LazyInitializationException when accessing
     * reviewComments after EntityManager.clear().
     *
     * @param repositoryId the repository ID
     * @return list of pull requests with review comments for the repository
     */
    @WorkspaceAgnostic("Backfill operation - Repository ID has workspace through organization")
    @Query(
        """
        SELECT DISTINCT p
        FROM PullRequest p
        LEFT JOIN FETCH p.reviewComments rc
        LEFT JOIN FETCH rc.author
        LEFT JOIN FETCH p.repository
        WHERE p.repository.id = :repositoryId
        """
    )
    List<PullRequest> findAllByRepositoryIdWithReviewComments(@Param("repositoryId") Long repositoryId);
}
