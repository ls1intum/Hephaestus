package de.tum.cit.aet.hephaestus.integration.scm.context;

import de.tum.cit.aet.hephaestus.core.WorkspaceAgnostic;
import de.tum.cit.aet.hephaestus.integration.core.spi.MentorUserCounts;
import de.tum.cit.aet.hephaestus.integration.core.spi.MentorWorkQuery;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.RequestedReviewer;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/** Read-side SCM queries scoped through monitored repositories and exact provider actors. */
@Repository
@WorkspaceAgnostic("Mentor context queries take workspace ID parameters")
public interface MentorContextQueryRepository extends JpaRepository<User, Long> {
    /**
     * One row per workspace, including zero counts for an account with no connected actor.
     * Each bucket counts the complete verified actor set without adding per-identity totals.
     */
    @Query("""
        SELECT new de.tum.cit.aet.hephaestus.integration.core.spi.MentorUserCounts(
            COALESCE((SELECT COUNT(DISTINCT p1)
                FROM PullRequest p1
                JOIN RepositoryToMonitor rtm1 ON rtm1.nameWithOwner = p1.repository.nameWithOwner
                    AND monitor_covers_repository(rtm1.workspace.id, rtm1.nativeId, p1.repository.nativeId, p1.repository.provider.id) = true
                WHERE p1.author.id IN :userIds
                  AND p1.deletedAt IS NULL
                  AND rtm1.workspace.id = :workspaceId
                  AND p1.state = de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue.State.OPEN), 0L),
            COALESCE((SELECT COUNT(DISTINCT p2)
                FROM PullRequest p2
                JOIN RepositoryToMonitor rtm2 ON rtm2.nameWithOwner = p2.repository.nameWithOwner
                    AND monitor_covers_repository(rtm2.workspace.id, rtm2.nativeId, p2.repository.nativeId, p2.repository.provider.id) = true
                WHERE p2.author.id IN :userIds
                  AND p2.deletedAt IS NULL
                  AND rtm2.workspace.id = :workspaceId
                  AND p2.isMerged = true
                  AND p2.mergedAt > :weekAgo
                  AND p2.mergedAt <= :now), 0L),
            COALESCE((SELECT COUNT(DISTINCT p3)
                FROM PullRequest p3
                JOIN RepositoryToMonitor rtm3 ON rtm3.nameWithOwner = p3.repository.nameWithOwner
                    AND monitor_covers_repository(rtm3.workspace.id, rtm3.nativeId, p3.repository.nativeId, p3.repository.provider.id) = true
                WHERE p3.author.id IN :userIds
                  AND p3.deletedAt IS NULL
                  AND rtm3.workspace.id = :workspaceId
                  AND p3.isMerged = true
                  AND p3.mergedAt > :twoWeeksAgo
                  AND p3.mergedAt <= :weekAgo), 0L),
            COALESCE((SELECT COUNT(DISTINCT i)
                FROM Issue i
                JOIN RepositoryToMonitor rtmi ON rtmi.nameWithOwner = i.repository.nameWithOwner
                    AND monitor_covers_repository(rtmi.workspace.id, rtmi.nativeId, i.repository.nativeId, i.repository.provider.id) = true
                WHERE TYPE(i) = Issue
                  AND i.author.id IN :userIds
                  AND i.deletedAt IS NULL
                  AND rtmi.workspace.id = :workspaceId
                  AND i.state = de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue.State.OPEN), 0L),
            COALESCE((SELECT COUNT(DISTINCT r1)
                FROM PullRequestReview r1
                JOIN RepositoryToMonitor rtmr1 ON rtmr1.nameWithOwner = r1.pullRequest.repository.nameWithOwner
                    AND monitor_covers_repository(rtmr1.workspace.id, rtmr1.nativeId, r1.pullRequest.repository.nativeId, r1.pullRequest.repository.provider.id) = true
                WHERE r1.author.id IN :userIds
                  AND r1.pullRequest.deletedAt IS NULL
                  AND rtmr1.workspace.id = :workspaceId
                  AND r1.submittedAt > :weekAgo
                  AND r1.submittedAt <= :now), 0L),
            COALESCE((SELECT COUNT(DISTINCT r2)
                FROM PullRequestReview r2
                JOIN RepositoryToMonitor rtmr2 ON rtmr2.nameWithOwner = r2.pullRequest.repository.nameWithOwner
                    AND monitor_covers_repository(rtmr2.workspace.id, rtmr2.nativeId, r2.pullRequest.repository.nativeId, r2.pullRequest.repository.provider.id) = true
                WHERE r2.author.id IN :userIds
                  AND r2.pullRequest.deletedAt IS NULL
                  AND rtmr2.workspace.id = :workspaceId
                  AND r2.submittedAt > :twoWeeksAgo
                  AND r2.submittedAt <= :weekAgo), 0L),
            COALESCE((SELECT COUNT(DISTINCT r3)
                FROM PullRequestReview r3
                JOIN RepositoryToMonitor rtmr3 ON rtmr3.nameWithOwner = r3.pullRequest.repository.nameWithOwner
                    AND monitor_covers_repository(rtmr3.workspace.id, rtmr3.nativeId, r3.pullRequest.repository.nativeId, r3.pullRequest.repository.provider.id) = true
                WHERE r3.pullRequest.author.id IN :userIds
                  AND r3.pullRequest.deletedAt IS NULL
                  AND rtmr3.workspace.id = :workspaceId
                  AND r3.submittedAt > :weekAgo
                  AND r3.submittedAt <= :now), 0L),
            COALESCE((SELECT COUNT(DISTINCT work)
                FROM PullRequest work
                JOIN RepositoryToMonitor rtmpr ON rtmpr.nameWithOwner = work.repository.nameWithOwner
                    AND monitor_covers_repository(rtmpr.workspace.id, rtmpr.nativeId, work.repository.nativeId, work.repository.provider.id) = true
                JOIN work.requestedReviewers request
                WHERE request.user.id IN :userIds
                  AND work.deletedAt IS NULL
                  AND work.isDraft = false
                  AND rtmpr.workspace.id = :workspaceId
                  AND work.state = de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue.State.OPEN
                  AND
        """ + RequestedReviewer.AWAITING_REVIEW + """
                ), 0L),
            COALESCE((SELECT COUNT(DISTINCT t)
                FROM PullRequestReviewThread t
                JOIN RepositoryToMonitor rtmt ON rtmt.nameWithOwner = t.pullRequest.repository.nameWithOwner
                    AND monitor_covers_repository(rtmt.workspace.id, rtmt.nativeId, t.pullRequest.repository.nativeId, t.pullRequest.repository.provider.id) = true
                WHERE t.pullRequest.author.id IN :userIds
                  AND t.pullRequest.deletedAt IS NULL
                  AND rtmt.workspace.id = :workspaceId
                  AND t.pullRequest.state = de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue.State.OPEN
                  AND t.state = de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThread.State.UNRESOLVED), 0L)
        )
        FROM Workspace scope
        WHERE scope.id = :workspaceId
        """)
    MentorUserCounts fetchUserCounts(
            @Param("workspaceId") Long workspaceId,
            @Param("userIds") Collection<Long> userIds,
            @Param("twoWeeksAgo") Instant twoWeeksAgo,
            @Param("weekAgo") Instant weekAgo,
            @Param("now") Instant now);

    /**
     * Open issues assigned to user, ordered most recent first, scoped to workspace.
     *
     * <p>{@code TYPE(i) = Issue} excludes the {@code PullRequest} subclass rows that share this table
     * under single-table inheritance — without it a GitLab workspace lists merge requests as "assigned
     * open issues", and this list disagrees with the typed open-issue count in
     * {@link #fetchUserCounts}, which already carries the discriminator.
     */
    @Query("""
        SELECT new de.tum.cit.aet.hephaestus.integration.core.spi.MentorWorkQuery$AssignedIssue(
            i.number,i.title,i.htmlUrl,repository.nameWithOwner,milestone.title,milestone.dueOn)
        FROM Issue i
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = i.repository.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, i.repository.nativeId, i.repository.provider.id) = true
        LEFT JOIN i.repository repository
        LEFT JOIN i.milestone milestone
        WHERE TYPE(i) = Issue
          AND EXISTS (SELECT assignee.id FROM User assignee
              WHERE assignee.id IN :userIds AND assignee MEMBER OF i.assignees)
          AND i.deletedAt IS NULL
          AND rtm.workspace.id = :workspaceId
          AND i.state = de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue.State.OPEN
        ORDER BY i.createdAt DESC
        """)
    List<MentorWorkQuery.AssignedIssue> findAssignedOpenIssues(
            @Param("workspaceId") Long workspaceId, @Param("userIds") Collection<Long> userIds);

    /**
     * Open, ready PRs that ask the user directly for a review they still owe ({@link RequestedReviewer#AWAITING_REVIEW}),
     * including those Activity shows as covered by other reviewers; a request to one of the user's teams is not one.
     * Author and repository fetched.
     */
    @Query("""
        SELECT DISTINCT new de.tum.cit.aet.hephaestus.integration.core.spi.MentorWorkQuery$RequestedReview(
            work.id,work.number,work.title,work.htmlUrl,author.login,work.createdAt)
        FROM PullRequest work
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = work.repository.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, work.repository.nativeId, work.repository.provider.id) = true
        JOIN work.requestedReviewers request
        LEFT JOIN work.author author
        WHERE request.user.id IN :userIds
          AND work.deletedAt IS NULL
          AND work.isDraft = false
          AND rtm.workspace.id = :workspaceId
          AND work.state = de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue.State.OPEN
          AND
        """ + RequestedReviewer.AWAITING_REVIEW + """
        ORDER BY work.createdAt DESC
        """)
    List<MentorWorkQuery.RequestedReview> findPendingReviewRequestPrs(
            @Param("workspaceId") Long workspaceId, @Param("userIds") Collection<Long> userIds);

    /** Uses the same author and monitored-repository scope as practice reviews; excludes tombstones. */
    @Query("""
        SELECT p
        FROM PullRequest p
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = p.repository.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, p.repository.nativeId, p.repository.provider.id) = true
        WHERE p.author.id IN :userIds
          AND p.deletedAt IS NULL
          AND rtm.workspace.id = :workspaceId
        ORDER BY p.createdAt DESC
        """)
    List<PullRequest> findRecentAuthoredPullRequests(
            @Param("workspaceId") Long workspaceId, @Param("userIds") Collection<Long> userIds, Pageable page);

    /** The stored copies of {@code ids} on the workspace's connected instance, for comparing with what was reviewed. */
    @Query("""
        SELECT p.id AS id, p.title AS title, p.body AS body, p.headRefOid AS head
        FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, r.nativeId, r.provider.id) = true
        WHERE p.id IN :ids
          AND p.deletedAt IS NULL
          AND rtm.workspace.id = :workspaceId
          AND r.provider.id = :providerId
        """)
    List<StoredWork> findStoredPullRequests(
            @Param("workspaceId") Long workspaceId,
            @Param("providerId") Long providerId,
            @Param("ids") Collection<Long> ids);

    /** {@link #findStoredPullRequests} for issues; an issue has no head. */
    @Query("""
        SELECT i.id AS id, i.title AS title, i.body AS body, CAST(NULL AS String) AS head
        FROM Issue i
        JOIN i.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, r.nativeId, r.provider.id) = true
        WHERE TYPE(i) = Issue
          AND i.id IN :ids
          AND i.deletedAt IS NULL
          AND rtm.workspace.id = :workspaceId
          AND r.provider.id = :providerId
        """)
    List<StoredWork> findStoredIssues(
            @Param("workspaceId") Long workspaceId,
            @Param("providerId") Long providerId,
            @Param("ids") Collection<Long> ids);

    interface StoredWork {
        Long getId();

        @Nullable
        String getTitle();

        @Nullable
        String getBody();

        @Nullable
        String getHead();
    }

    /**
     * The developer's open authored pull requests on the workspace's connected instance, most recently
     * updated first. The provider predicate keeps a same-path project on another instance out: the
     * monitor matches by path alone.
     */
    @Query("""
        SELECT p
        FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, r.nativeId, r.provider.id) = true
        WHERE p.author.id IN :userIds
          AND p.deletedAt IS NULL
          AND p.state = de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue.State.OPEN
          AND rtm.workspace.id = :workspaceId
          AND r.provider.id = :providerId
        ORDER BY p.updatedAt DESC NULLS LAST, p.id DESC
        """)
    List<PullRequest> findOpenAuthoredPullRequestsOnInstance(
            @Param("workspaceId") Long workspaceId,
            @Param("userIds") Collection<Long> userIds,
            @Param("providerId") Long providerId,
            Pageable page);

    /**
     * One pull request the developer authored, in whatever state is stored — open, closed or merged — under the
     * scope of {@link #findOpenAuthoredPullRequestsOnInstance} otherwise: monitored by the workspace, on its connected
     * instance, not tombstoned.
     */
    @Query("""
        SELECT p
        FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, r.nativeId, r.provider.id) = true
        WHERE p.id = :pullRequestId
          AND p.author.id IN :userIds
          AND p.deletedAt IS NULL
          AND rtm.workspace.id = :workspaceId
          AND r.provider.id = :providerId
        """)
    List<PullRequest> findAuthoredPullRequestOnInstance(
            @Param("workspaceId") Long workspaceId,
            @Param("userIds") Collection<Long> userIds,
            @Param("providerId") Long providerId,
            @Param("pullRequestId") Long pullRequestId);

    /**
     * The issues the provider records {@code pullRequestId} as closing — GitHub's closing references, GitLab's
     * closes-issues — under the same scope as {@link #findAuthoredPullRequestOnInstance}: a pull request the
     * developer authored, in any stored state, monitored by the workspace, on its connected instance. Only issues of
     * the pull request's own repository that are not tombstoned, lowest number first; a caller passes its cap plus
     * one to learn whether more exist.
     */
    @Query("""
        SELECT DISTINCT i
        FROM PullRequest p
        JOIN p.repository r
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = r.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, r.nativeId, r.provider.id) = true
        JOIN p.closingIssues i
        WHERE p.id = :pullRequestId
          AND p.author.id IN :userIds
          AND p.deletedAt IS NULL
          AND rtm.workspace.id = :workspaceId
          AND r.provider.id = :providerId
          AND TYPE(i) = Issue
          AND i.repository = p.repository
          AND i.deletedAt IS NULL
        ORDER BY i.number ASC, i.id ASC
        """)
    List<Issue> findClosingIssuesOfAuthoredPullRequest(
            @Param("workspaceId") Long workspaceId,
            @Param("userIds") Collection<Long> userIds,
            @Param("providerId") Long providerId,
            @Param("pullRequestId") Long pullRequestId,
            Pageable page);

    /** A pull request's inline comments, a thread at a time: unresolved threads first, each thread oldest first. */
    @Query("""
        SELECT c
        FROM PullRequestReviewComment c
        JOIN FETCH c.thread t
        LEFT JOIN FETCH t.resolvedBy
        LEFT JOIN FETCH c.author
        WHERE c.pullRequest.id = :pullRequestId
          AND c.pullRequest.deletedAt IS NULL
        ORDER BY CASE WHEN t.state = de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThread.State.UNRESOLVED THEN 0 ELSE 1 END,
                 t.id DESC, c.createdAt ASC, c.id ASC
        """)
    List<PullRequestReviewComment> findThreadCommentsUnresolvedFirst(
            @Param("pullRequestId") Long pullRequestId, Pageable page);

    /**
     * The developer's own authored ISSUES (excluding PRs via {@code TYPE(i) = Issue}) in the workspace,
     * newest first. Companion to {@link #findRecentAuthoredPullRequests}.
     */
    @Query("""
        SELECT i
        FROM Issue i
        JOIN RepositoryToMonitor rtm ON rtm.nameWithOwner = i.repository.nameWithOwner
            AND monitor_covers_repository(rtm.workspace.id, rtm.nativeId, i.repository.nativeId, i.repository.provider.id) = true
        WHERE i.author.id IN :userIds
          AND i.deletedAt IS NULL
          AND rtm.workspace.id = :workspaceId
          AND TYPE(i) = Issue
        ORDER BY i.createdAt DESC
        """)
    List<Issue> findRecentAuthoredIssues(
            @Param("workspaceId") Long workspaceId, @Param("userIds") Collection<Long> userIds, Pageable page);
}
