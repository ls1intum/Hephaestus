package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import de.tum.in.www1.hephaestus.practices.model.BadPracticeDetection;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for bad practice detection records.
 *
 * <p>Workspace-agnostic: All queries filter by PullRequest ID which has
 * workspace context through {@code pullRequest.repository.organization.workspaceId}.
 */
@Repository
@WorkspaceAgnostic("Queries filter by PullRequest ID which has workspace through repository.organization")
public interface BadPracticeDetectionRepository extends JpaRepository<BadPracticeDetection, Long> {
    /**
     * Finds the most recent detection for a given pull request.
     *
     * <p>Workspace-agnostic: PullRequest ID inherently has workspace through
     * repository.organization.workspaceId chain.
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
          AND bpd.detectionTime = (
            SELECT MAX(bpd2.detectionTime)
            FROM BadPracticeDetection bpd2
            WHERE bpd2.pullRequest.id = bpd.pullRequest.id
          )
        """
    )
    BadPracticeDetection findMostRecentByPullRequestId(@Param("pullRequestId") long pullRequestId);
}
