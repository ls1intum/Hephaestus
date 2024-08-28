package de.tum.in.www1.hephaestus.codereview.pullrequest;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestRepository extends JpaRepository<PullRequest, Long> {

    @Query("SELECT p FROM Pullrequest p WHERE p.githubId = ?1")
    PullRequest findByGithubId(Long githubId);

    @Query("SELECT p FROM Pullrequest p, Actor a WHERE p.author = a AND a.login = ?1")
    List<PullRequest> findByAuthor(String authorLogin);

}
