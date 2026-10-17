package de.tum.cit.aet.hephaestus.integration.core.spi;

import java.time.Instant;
import java.util.List;
import org.jspecify.annotations.Nullable;

/** Workspace-scoped work summaries supplied by the connected domain adapter. */
public interface MentorWorkQuery {
    MentorUserCounts fetchUserCounts(
            Long workspaceId, Long accountId, Instant twoWeeksAgo, Instant weekAgo, Instant now);

    List<AssignedIssue> findAssignedOpenIssues(Long workspaceId, Long accountId);

    List<RequestedReview> findPendingReviewRequestPrs(Long workspaceId, Long accountId);

    record AssignedIssue(
            int number,
            @Nullable String title,
            @Nullable String url,
            @Nullable String repository,
            @Nullable String milestoneTitle,
            @Nullable Instant milestoneDueOn) {}

    record RequestedReview(
            long id,
            int number,
            @Nullable String title,
            @Nullable String url,
            @Nullable String author,
            @Nullable Instant createdAt) {}
}
