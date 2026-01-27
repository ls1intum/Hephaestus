package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for pull request bad practice records.
 *
 * <p>Workspace-agnostic: All queries filter by PullRequest ID which has
 * workspace context through the Repository-to-Workspace relationship.
 */
@Repository
@WorkspaceAgnostic("Queries filter by PullRequest ID which has workspace through repository")
public interface PullRequestBadPracticeRepository extends JpaRepository<PullRequestBadPractice, Long> {
    /**
     * Finds the most recent version of each bad practice for a given pull request.
     * Groups by title and returns only the latest detection for each.
     *
     * <p>Workspace-agnostic: PullRequest ID inherently has workspace through
     * the repository relationship.
     *
     * @param pullRequestId the pull request ID
     * @return list of bad practices with their most recent state
     */
    @Transactional
    @Query(
        """
        SELECT prbp
        FROM PullRequestBadPractice prbp
        WHERE prbp.pullRequest.id = :pullRequestId
          AND prbp.detectedAt = (
            SELECT MAX(prbp2.detectedAt)
            FROM PullRequestBadPractice prbp2
            WHERE prbp2.title = prbp.title
              AND prbp2.pullRequest.id = prbp.pullRequest.id)
        """
    )
    List<PullRequestBadPractice> findByPullRequestId(@Param("pullRequestId") long pullRequestId);

    /**
     * Finds the most recent version of each bad practice for multiple pull requests.
     * Groups by title and returns only the latest detection for each.
     *
     * <p>Workspace-agnostic: PullRequest IDs inherently have workspace through
     * the repository relationship.
     *
     * @param pullRequestIds the set of pull request IDs
     * @return list of bad practices with their most recent state
     */
    @Transactional
    @Query(
        """
        SELECT prbp
        FROM PullRequestBadPractice prbp
        WHERE prbp.pullRequest.id IN :pullRequestIds
          AND prbp.detectedAt = (
            SELECT MAX(prbp2.detectedAt)
            FROM PullRequestBadPractice prbp2
            WHERE prbp2.title = prbp.title
              AND prbp2.pullRequest.id = prbp.pullRequest.id)
        """
    )
    List<PullRequestBadPractice> findByPullRequestIds(@Param("pullRequestIds") Set<Long> pullRequestIds);

    /**
     * Finds bad practices for multiple pull requests and returns them grouped by PR ID.
     *
     * @param pullRequestIds the set of pull request IDs
     * @return map of pull request ID to list of bad practices
     */
    default Map<Long, List<PullRequestBadPractice>> findByPullRequestIdsAsMap(Set<Long> pullRequestIds) {
        if (pullRequestIds.isEmpty()) {
            return Map.of();
        }
        return findByPullRequestIds(pullRequestIds)
            .stream()
            .collect(Collectors.groupingBy(badPractice -> badPractice.getPullRequest().getId()));
    }
}
