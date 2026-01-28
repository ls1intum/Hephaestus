package de.tum.in.www1.hephaestus.gitprovider.pullrequest;

import jakarta.persistence.QueryHint;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for PullRequest entities.
 *
 * <p>This repository contains only domain-agnostic queries for the gitprovider domain.
 * Scope-filtered queries (those that join with RepositoryToMonitor or other consuming module
 * entities) belong in the consuming packages (leaderboard, profile, practices, etc.)
 * to maintain clean architecture boundaries.
 *
 * @see de.tum.in.www1.hephaestus.profile.ProfilePullRequestQueryRepository
 */
@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {
    /**
     * Finds a PR by repository ID and number for sync operations.
     * Repository ID inherently has scope through Organization.
     */
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
     * Finds a pull request by ID with assignees eagerly fetched.
     * Useful when assignees need to be accessed outside the original transaction,
     * avoiding LazyInitializationException after the Hibernate session is closed.
     *
     * @param id the pull request ID
     * @return the pull request with assignees loaded, or empty if not found
     */
    @Query(
        """
        SELECT p FROM PullRequest p
        LEFT JOIN FETCH p.assignees
        WHERE p.id = :id
        """
    )
    Optional<PullRequest> findByIdWithAssignees(@Param("id") Long id);

    /**
     * Finds a pull request by ID with repository eagerly fetched.
     * Required for passing PRs across transaction boundaries where the repository
     * relationship needs to be accessed (e.g., for logging nameWithOwner).
     * Avoids LazyInitializationException when PR is fetched in one transaction
     * and repository is accessed in another.
     *
     * @param id the pull request ID
     * @return the pull request with repository loaded, or empty if not found
     */
    @Query(
        """
        SELECT p FROM PullRequest p
        LEFT JOIN FETCH p.repository
        WHERE p.id = :id
        """
    )
    Optional<PullRequest> findByIdWithRepository(@Param("id") Long id);

    /**
     * Finds all pull requests belonging to a repository.
     * Repository ID inherently has scope through Organization.
     *
     * @param repositoryId the repository ID
     * @return list of pull requests for the repository
     */
    List<PullRequest> findAllByRepository_Id(Long repositoryId);

    /**
     * Finds pull requests belonging to a repository with pagination.
     * Uses Slice for efficient batching without requiring a count query.
     * Repository ID inherently has scope through Organization.
     *
     * @param repositoryId the repository ID
     * @param pageable pagination parameters
     * @return slice of pull requests for the repository
     */
    Slice<PullRequest> findByRepository_Id(Long repositoryId, Pageable pageable);

    /**
     * Streams all pull requests belonging to a repository.
     * Repository ID inherently has scope through Organization.
     * <p>
     * Must be used within a try-with-resources block to ensure the stream is closed
     * and the database connection is released. The calling method must be annotated
     * with @Transactional(readOnly = true) for streaming to work properly.
     *
     * @param repositoryId the repository ID
     * @return stream of pull requests for the repository
     */
    @QueryHints(@QueryHint(name = org.hibernate.jpa.HibernateHints.HINT_FETCH_SIZE, value = "50"))
    Stream<PullRequest> streamAllByRepository_Id(Long repositoryId);
}
