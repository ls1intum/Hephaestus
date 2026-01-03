package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for pull request review comment entities.
 *
 * <p>Workspace-agnostic: Comments are scoped through their thread which has
 * workspace context through {@code thread.pullRequest.repository.organization.workspaceId}.
 * All queries filter by thread ID which inherently carries workspace scope.
 */
@WorkspaceAgnostic("Scoped through thread.pullRequest.repository.organization.workspaceId")
public interface PullRequestReviewCommentRepository extends JpaRepository<PullRequestReviewComment, Long> {
    boolean existsByThreadId(Long threadId);

    long countByThreadId(Long threadId);
}
