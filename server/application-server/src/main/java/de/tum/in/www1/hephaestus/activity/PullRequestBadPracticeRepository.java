package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPractice;
import jakarta.transaction.Transactional;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestBadPracticeRepository extends JpaRepository<PullRequestBadPractice, Long> {
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
