package de.tum.in.www1.hephaestus.gitprovider.issue;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Repository for issue entities.
 *
 * <p>All queries filter by repository ID which inherently carries scope
 * through the Repository -> Organization relationship chain.
 */
public interface IssueRepository extends JpaRepository<Issue, Long> {
    /**
     * Finds an issue by repository ID and number.
     * This is the canonical lookup for sync operations as it uses the natural key
     * (repository_id, number) which is consistent across both GraphQL sync and
     * webhook events, regardless of which ID format they use.
     *
     * @param repositoryId the repository ID
     * @param number the issue number within the repository
     * @return the issue if found
     */
    @Query(
        """
        SELECT i
        FROM Issue i
        LEFT JOIN FETCH i.labels
        LEFT JOIN FETCH i.author
        LEFT JOIN FETCH i.assignees
        LEFT JOIN FETCH i.repository
        WHERE i.repository.id = :repositoryId AND i.number = :number
        """
    )
    Optional<Issue> findByRepositoryIdAndNumber(@Param("repositoryId") long repositoryId, @Param("number") int number);

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
     * Nullifies milestone references on all issues that reference the given milestone.
     * <p>
     * This is a direct database update that doesn't rely on Hibernate's collection state.
     * MUST be called before deleting a milestone to avoid foreign key constraint violations.
     * <p>
     * Unlike {@code milestone.removeAllIssues()} which operates on the in-memory collection
     * (which may be stale or not fully loaded), this method updates ALL issues in the
     * database that reference the milestone.
     *
     * @param milestoneId the milestone ID to detach from issues
     * @return the number of issues updated
     */
    @Modifying
    @Query("UPDATE Issue i SET i.milestone = null WHERE i.milestone.id = :milestoneId")
    int clearMilestoneReferences(@Param("milestoneId") Long milestoneId);
}
