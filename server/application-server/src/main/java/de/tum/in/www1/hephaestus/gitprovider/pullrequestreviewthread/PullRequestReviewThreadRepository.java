package de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread;

import de.tum.in.www1.hephaestus.core.WorkspaceAgnostic;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Repository for pull request review thread entities.
 *
 * <p>Workspace-agnostic: Threads are scoped through their pull request which has
 * workspace context through the Workspace.organization relationship.
 * All queries filter by thread ID which inherently carries workspace scope.
 */
@WorkspaceAgnostic("Scoped through Workspace.organization relationship")
public interface PullRequestReviewThreadRepository extends JpaRepository<PullRequestReviewThread, Long> {
    @EntityGraph(attributePaths = "comments")
    Optional<PullRequestReviewThread> findWithCommentsById(Long id);
}
