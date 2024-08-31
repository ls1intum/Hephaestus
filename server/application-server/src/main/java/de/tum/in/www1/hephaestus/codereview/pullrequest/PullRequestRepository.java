package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.Set;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    Set<PullRequest> findByAuthor_Login(String authorLogin);
}
