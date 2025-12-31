package de.tum.in.www1.hephaestus.activity.badpractice;

import de.tum.in.www1.hephaestus.activity.model.BadPracticeDetection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for bad practice detection records.
 */
@Repository
public interface BadPracticeDetectionRepository extends JpaRepository<BadPracticeDetection, Long> {
    /**
     * Finds the most recent detection for a given pull request.
     *
     * @param pullRequestId the pull request ID
     * @return the most recent detection, or null if none exists
     */
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
