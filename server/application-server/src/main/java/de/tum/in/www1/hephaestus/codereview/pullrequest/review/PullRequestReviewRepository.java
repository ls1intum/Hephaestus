package de.tum.in.www1.hephaestus.codereview.pullrequest.review;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {

    Optional<PullRequestReview> findByOwner_Login(String ownerLogin);

    Optional<PullRequestReview> findByPullRequest(PullRequestReview pullRequest);
}
