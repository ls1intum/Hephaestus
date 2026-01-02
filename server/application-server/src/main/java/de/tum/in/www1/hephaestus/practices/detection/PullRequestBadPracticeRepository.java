package de.tum.in.www1.hephaestus.practices.detection;

import de.tum.in.www1.hephaestus.practices.model.PullRequestBadPractice;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for pull request bad practice records.
 */
@Repository
public interface PullRequestBadPracticeRepository extends JpaRepository<PullRequestBadPractice, Long> {
    /**
     * Finds the most recent version of each bad practice for a given pull request.
     * Groups by title and returns only the latest detection for each.
     *
     * @param pullRequestId the pull request ID
     * @return list of bad practices with their most recent state
     */
    @Transactional
    @Query(
        """
        SELECT prbp
        FROM PullRequestBadPractice prbp
        WHERE prbp.pullrequest.id = :pullRequestId
          AND prbp.detectionTime = (
            SELECT MAX(prbp2.detectionTime)
            FROM PullRequestBadPractice prbp2
            WHERE prbp2.title = prbp.title
              AND prbp2.pullrequest.id = prbp.pullrequest.id)
        """
    )
    List<PullRequestBadPractice> findByPullRequestId(@Param("pullRequestId") long pullRequestId);
}
