package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PullRequestReviewThreadRepository extends JpaRepository<PullRequestReviewThread, Long> {
    @EntityGraph(attributePaths = "comments")
    Optional<PullRequestReviewThread> findWithCommentsById(Long id);
}
