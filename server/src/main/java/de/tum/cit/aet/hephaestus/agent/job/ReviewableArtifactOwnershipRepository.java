package de.tum.cit.aet.hephaestus.agent.job;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Whether a workspace may act on a mirrored SCM artifact named by its surrogate id.
 *
 * <p>An artifact carries no workspace column: a repository belongs to a workspace through a
 * {@code RepositoryToMonitor} mapping, so ownership is a join and cannot be read off the row. That join
 * lives here rather than on the SCM repositories, which are deliberately workspace-agnostic and whose
 * contract puts monitor joins in the consuming package.
 *
 * <p>Kept separate from the gate loader's fetch queries instead of folded into them. Those queries carry
 * the eager graph the review path needs and are exercised by the production listeners; ownership is one
 * boolean, and an extra round trip on a dev-only route is cheaper than a second copy of a five-way
 * {@code JOIN FETCH} that could drift from the one under test.
 *
 * <p>{@code TYPE} discriminates in both queries: {@code Issue} and {@code PullRequest} share one table
 * under {@code SINGLE_TABLE} inheritance, so an id lookup without it would answer for the wrong kind.
 */
@Repository
@WorkspaceAgnostic("Ownership is the question; the workspace id is the parameter it is asked about")
interface ReviewableArtifactOwnershipRepository extends JpaRepository<Issue, Long> {
    @Query(
        """
        SELECT COUNT(p) > 0 FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId AND p.id = :pullRequestId AND TYPE(p) = PullRequest
        """
    )
    boolean pullRequestBelongsToWorkspace(
        @Param("workspaceId") Long workspaceId,
        @Param("pullRequestId") Long pullRequestId
    );

    @Query(
        """
        SELECT COUNT(i) > 0 FROM Issue i
        JOIN i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId AND i.id = :issueId AND TYPE(i) = Issue
        """
    )
    boolean issueBelongsToWorkspace(@Param("workspaceId") Long workspaceId, @Param("issueId") Long issueId);
}
