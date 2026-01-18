package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeDetection;
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
 * Repository for bad practice detection records.
 *
 * <p>Workspace-agnostic: All queries filter by PullRequest ID which has
 * workspace context through the Workspace.organization relationship.
 */
@Repository
@WorkspaceAgnostic("Queries filter by PullRequest ID which has workspace through repository")
public interface BadPracticeDetectionRepository extends JpaRepository<BadPracticeDetection, Long> {
    /**
     * Finds the most recent detection for a given pull request.
     *
     * <p>Workspace-agnostic: PullRequest ID inherently has workspace through
     * the repository relationship.
     *
     * @param pullRequestId the pull request ID
     * @return the most recent detection, or null if none exists
     */
    @Transactional
    @Query(
        """
        SELECT bpd
        FROM BadPracticeDetection bpd
        WHERE bpd.pullRequest.id = :pullRequestId
          AND bpd.detectedAt = (
            SELECT MAX(bpd2.detectedAt)
            FROM BadPracticeDetection bpd2
            WHERE bpd2.pullRequest.id = bpd.pullRequest.id
          )
        """
    )
    BadPracticeDetection findMostRecentByPullRequestId(@Param("pullRequestId") long pullRequestId);

    /**
     * Finds the most recent detection for each of the given pull requests.
     *
     * <p>Workspace-agnostic: PullRequest IDs inherently have workspace through
     * the repository relationship.
     *
     * @param pullRequestIds the set of pull request IDs
     * @return list of most recent detections for each PR that has detections
     */
    @Transactional
    @Query(
        """
        SELECT bpd
        FROM BadPracticeDetection bpd
        WHERE bpd.pullRequest.id IN :pullRequestIds
          AND bpd.detectedAt = (
            SELECT MAX(bpd2.detectedAt)
            FROM BadPracticeDetection bpd2
            WHERE bpd2.pullRequest.id = bpd.pullRequest.id
          )
        """
    )
    List<BadPracticeDetection> findMostRecentByPullRequestIds(@Param("pullRequestIds") Set<Long> pullRequestIds);

    /**
     * Finds the most recent detections for multiple pull requests and returns them as a map.
     *
     * @param pullRequestIds the set of pull request IDs
     * @return map of pull request ID to most recent detection
     */
    default Map<Long, BadPracticeDetection> findMostRecentByPullRequestIdsAsMap(Set<Long> pullRequestIds) {
        if (pullRequestIds.isEmpty()) {
            return Map.of();
        }
        return findMostRecentByPullRequestIds(pullRequestIds).stream()
            .collect(Collectors.toMap(
                detection -> detection.getPullRequest().getId(),
                detection -> detection
            ));
    }
}
