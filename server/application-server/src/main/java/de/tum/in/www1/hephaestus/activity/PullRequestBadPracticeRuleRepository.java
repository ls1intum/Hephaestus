package de.tum.in.www1.hephaestus.activity;

import de.tum.in.www1.hephaestus.activity.model.PullRequestBadPracticeRule;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PullRequestBadPracticeRuleRepository extends JpaRepository<PullRequestBadPracticeRule, Long> {

    @Query(
        """
        SELECT prbp
        FROM PullRequestBadPracticeRule prbp
        WHERE prbp.repository.nameWithOwner = :repositoryNameWithOwner
        """
    )
    List<PullRequestBadPracticeRule> findByRepositoryName(String repositoryNameWithOwner);
}
