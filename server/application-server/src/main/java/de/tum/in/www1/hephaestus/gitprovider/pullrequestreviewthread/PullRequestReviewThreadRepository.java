package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for pull request review thread entities.
 *
 * <p>Threads are scoped through their pull request which has scope through
 * the PullRequest -> Repository -> Organization chain.
 */
public interface PullRequestReviewThreadRepository extends JpaRepository<PullRequestReviewThread, Long> {
    @EntityGraph(attributePaths = "comments")
    Optional<PullRequestReviewThread> findWithCommentsById(Long id);
}
