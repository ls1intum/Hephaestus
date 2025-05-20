package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.BadPracticeDetection;
import jakarta.transaction.Transactional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface BadPracticeDetectionRepository extends JpaRepository<BadPracticeDetection, Long> {
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
