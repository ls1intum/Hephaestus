package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestBadPracticeRuleRepository extends JpaRepository<PullRequestBadPracticeRule, Long> {
    @Query(
        """
        SELECT prbp
        FROM PullRequestBadPracticeRule prbp
        WHERE prbp.active = true
        """
    )
    List<PullRequestBadPracticeRule> findAllActive();
}
