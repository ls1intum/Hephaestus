package de.tum.in.www1.hephaestus.agent.context.providers.mentor;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import de.tum.in.www1.hephaestus.mentor.ChatThread;
import java.time.Instant;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Read-side queries for mentor aspect providers. {@code User} is the bound root only because
 * Spring Data requires one; the queries themselves cross several entities, each
 * workspace-scoped through {@code RepositoryToMonitor} or {@code Practice.workspace}.
 */
@Repository
public interface MentorAspectQueryRepository extends JpaRepository<User, Long> {
    /** Open PRs authored by user within workspace. */
    @Query(
        """
        SELECT COUNT(p)
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND p.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN
        """
    )
    long countOpenPullRequests(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /** PRs merged by author within a time window. */
    @Query(
        """
        SELECT COUNT(p)
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        WHERE p.author.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND p.isMerged = true
          AND p.mergedAt > :since
          AND p.mergedAt <= :until
        """
    )
    long countMergedPullRequestsInWindow(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /** Open authored issues (not PRs) for the user within workspace. */
    @Query(
        """
        SELECT COUNT(i)
        FROM Issue i
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = i.repository.nameWithOwner
        WHERE TYPE(i) = Issue
          AND i.author.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND i.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN
        """
    )
    long countOpenAuthoredIssues(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /** Reviews submitted by user in a time window, scoped to workspace via PR repository. */
    @Query(
        """
        SELECT COUNT(prr)
        FROM PullRequestReview prr
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = prr.pullRequest.repository.nameWithOwner
        WHERE prr.author.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND prr.submittedAt > :since
          AND prr.submittedAt <= :until
        """
    )
    long countReviewsGivenInWindow(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId,
        @Param("since") Instant since,
        @Param("until") Instant until
    );

    /** Reviews received on PRs the user authored within a time window. */
    @Query(
        """
        SELECT COUNT(prr)
        FROM PullRequestReview prr
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = prr.pullRequest.repository.nameWithOwner
        WHERE prr.pullRequest.author.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND prr.submittedAt > :since
        """
    )
    long countReviewsReceivedSince(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId,
        @Param("since") Instant since
    );

    /** Pending review requests on still-open PRs targeting user, scoped to workspace. */
    @Query(
        """
        SELECT COUNT(p)
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        JOIN p.requestedReviewers reviewer
        WHERE reviewer.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND p.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN
        """
    )
    long countPendingReviewRequests(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /** Unresolved review threads on the user's own open PRs (an "actionable feedback" signal). */
    @Query(
        """
        SELECT COUNT(t)
        FROM PullRequestReviewThread t
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = t.pullRequest.repository.nameWithOwner
        WHERE t.pullRequest.author.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND t.pullRequest.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN
          AND t.state = de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread.State.UNRESOLVED
        """
    )
    long countUnresolvedThreadsOnAuthoredPrs(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /** Open issues assigned to user, ordered most recent first, scoped to workspace. */
    @Query(
        """
        SELECT i
        FROM Issue i
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = i.repository.nameWithOwner
        JOIN i.assignees a
        LEFT JOIN FETCH i.repository
        LEFT JOIN FETCH i.milestone
        WHERE a.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND i.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN
        ORDER BY i.createdAt DESC
        """
    )
    List<Issue> findAssignedOpenIssues(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /** Open PRs where user has been requested to review, with author + repo fetched. */
    @Query(
        """
        SELECT p
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
        JOIN p.requestedReviewers reviewer
        LEFT JOIN FETCH p.author
        LEFT JOIN FETCH p.repository
        WHERE reviewer.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND p.state = de.tum.in.www1.hephaestus.gitprovider.issue.Issue.State.OPEN
        ORDER BY p.createdAt DESC
        """
    )
    List<PullRequest> findPendingReviewRequestPrs(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    /** Recent chat threads for the user within the workspace, newest first. */
    @Query(
        """
        SELECT t
        FROM ChatThread t
        WHERE t.workspace.id = :workspaceId
          AND t.user.id = :userId
        ORDER BY t.createdAt DESC
        """
    )
    List<ChatThread> findRecentChatThreads(@Param("workspaceId") Long workspaceId, @Param("userId") Long userId);

    // ════════════════════════════════════════════════════════════════════════
    // Findings aspect — reviews received in window
    // ════════════════════════════════════════════════════════════════════════

    /**
     * Earliest USER-role message per thread for a batch of thread ids. One round trip instead of
     * the N-trips loop the previous `firstUserMessagePreview(threadId)` did. Native because
     * Postgres `DISTINCT ON` is the cheapest path; equivalent JPQL would need a correlated
     * subquery for every thread anyway. Joins {@code chat_thread} on {@code workspace_id} so the
     * query refuses cross-tenant ids the caller might pass — defence in depth even though the
     * upstream id list is already user-scoped.
     */
    @Query(
        value = """
        SELECT DISTINCT ON (m.thread_id) m.thread_id, m.parts
          FROM chat_message m
          JOIN chat_thread t ON t.id = m.thread_id
         WHERE m.thread_id IN (:threadIds)
           AND t.workspace_id = :workspaceId
           AND m.role = 'USER'
         ORDER BY m.thread_id, m.created_at ASC
        """,
        nativeQuery = true
    )
    List<Object[]> findFirstUserMessagePartsByThreadIds(
        @Param("workspaceId") Long workspaceId,
        @Param("threadIds") List<java.util.UUID> threadIds
    );

    /** Reviews received on user's authored PRs within {@code since}..now, scoped to workspace. */
    @Query(
        """
        SELECT prr
        FROM PullRequestReview prr
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = prr.pullRequest.repository.nameWithOwner
        LEFT JOIN FETCH prr.author
        LEFT JOIN FETCH prr.pullRequest
        WHERE prr.pullRequest.author.id = :userId
          AND rtm.workspace.id = :workspaceId
          AND prr.submittedAt > :since
        ORDER BY prr.submittedAt DESC
        """
    )
    List<PullRequestReview> findReviewsReceivedSince(
        @Param("workspaceId") Long workspaceId,
        @Param("userId") Long userId,
        @Param("since") Instant since
    );
}
