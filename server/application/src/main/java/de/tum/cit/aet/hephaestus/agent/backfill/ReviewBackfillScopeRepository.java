package de.tum.cit.aet.hephaestus.agent.backfill;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import java.time.Instant;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Enumerates the artifacts a campaign covers — once to count them for the estimate, then in ascending-id
 * pages to walk them.
 *
 * <p><strong>Every query discriminates on {@code TYPE}.</strong> {@code Issue} and {@code PullRequest}
 * share one table under {@code SINGLE_TABLE} inheritance, so a query without a type predicate silently
 * includes pull/merge requests too — double-counting the estimate and submitting issue reviews for pull
 * requests.
 *
 * <p>Tombstoned rows ({@code deletedAt} set) are excluded here even though most read paths surface them:
 * a campaign spends money reviewing artifacts, and reviewing one that no longer exists upstream is
 * indefensible.
 */
@Repository
@WorkspaceAgnostic("Scope enumeration takes the workspace id as a parameter")
public interface ReviewBackfillScopeRepository extends JpaRepository<Issue, Long> {
    @Query("""
        SELECT COUNT(p) FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId
          AND TYPE(p) = PullRequest
          AND p.deletedAt IS NULL
          AND p.createdAt >= :fromAt
          AND p.createdAt < :toAt
        """)
    long countPullRequests(
            @Param("workspaceId") Long workspaceId, @Param("fromAt") Instant fromAt, @Param("toAt") Instant toAt);

    @Query("""
        SELECT p.id FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId
          AND TYPE(p) = PullRequest
          AND p.deletedAt IS NULL
          AND p.createdAt >= :fromAt
          AND p.createdAt < :toAt
          AND p.id > :afterId
        ORDER BY p.id ASC
        """)
    List<Long> findPullRequestIds(
            @Param("workspaceId") Long workspaceId,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("afterId") Long afterId,
            Pageable pageable);

    @Query("""
        SELECT COUNT(i) FROM Issue i
        JOIN i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId
          AND TYPE(i) = Issue
          AND i.deletedAt IS NULL
          AND i.createdAt >= :fromAt
          AND i.createdAt < :toAt
        """)
    long countIssues(
            @Param("workspaceId") Long workspaceId, @Param("fromAt") Instant fromAt, @Param("toAt") Instant toAt);

    @Query("""
        SELECT i.id FROM Issue i
        JOIN i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
        WHERE rtm.workspace.id = :workspaceId
          AND TYPE(i) = Issue
          AND i.deletedAt IS NULL
          AND i.createdAt >= :fromAt
          AND i.createdAt < :toAt
          AND i.id > :afterId
        ORDER BY i.id ASC
        """)
    List<Long> findIssueIds(
            @Param("workspaceId") Long workspaceId,
            @Param("fromAt") Instant fromAt,
            @Param("toAt") Instant toAt,
            @Param("afterId") Long afterId,
            Pageable pageable);
}
