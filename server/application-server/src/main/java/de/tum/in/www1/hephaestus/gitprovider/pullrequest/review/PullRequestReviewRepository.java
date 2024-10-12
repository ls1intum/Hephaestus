package de.tum.in.www1.hephaestus.gitprovider.pullrequest.review;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

@Repository
public interface PullRequestReviewRepository extends JpaRepository<PullRequestReview, Long> {

    Optional<PullRequestReview> findByAuthor_Login(String authorLogin);

    Optional<PullRequestReview> findByPullRequest(PullRequestReview pullRequest);

    @Query("""
                SELECT pr
                FROM PullRequestReview pr
                JOIN FETCH pr.comments
                WHERE pr.id = :reviewId
            """)
    Optional<PullRequestReview> findByIdWithEagerComments(Long reviewId);
}
