package de.tum.in.www1.hephaestus.activity;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface PullRequestBadPracticeRepository extends JpaRepository<PullRequestBadPractice, Long> {

    @Query("""
            SELECT prbp
            FROM PullRequestBadPractice prbp
            WHERE LOWER(:assigneeLogin) IN (SELECT LOWER(u.login) FROM prbp.pullrequest.assignees u) AND prbp.pullrequest.state = 'OPEN'
            """)
    List<PullRequestBadPractice> findByLogin(@Param("assigneeLogin") String assigneeLogin);
}
