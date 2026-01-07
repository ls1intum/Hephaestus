package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for pull request review comment entities.
 *
 * <p>Comments are scoped through their thread which has workspace context through
 * the Thread -> PullRequest -> Repository -> Organization -> Workspace.organization chain.
 */
public interface PullRequestReviewCommentRepository extends JpaRepository<PullRequestReviewComment, Long> {
    boolean existsByThreadId(Long threadId);

    long countByThreadId(Long threadId);
}
