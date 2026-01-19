package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Immutable event payload DTOs for domain events.
 * These records are safe for async handling - no lazy-loaded relationships.
 */
public final class EventPayload {

    private static final Logger log = LoggerFactory.getLogger(EventPayload.class);

    private EventPayload() {}

    // ========================================================================
    // Issue Event Payload
    // ========================================================================

    /**
     * Immutable snapshot of an Issue for event handling.
     */
    public record IssueData(
        @NonNull Long id,
        int number,
        @NonNull String title,
        @Nullable String body,
        @NonNull Issue.State state,
        @Nullable String stateReason,
        @NonNull String htmlUrl,
        boolean isPullRequest,
        @NonNull RepositoryRef repository,
        @Nullable Long authorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable Instant closedAt
    ) {
        public static IssueData from(Issue issue) {
            return new IssueData(
                issue.getId(),
                issue.getNumber(),
                issue.getTitle(),
                issue.getBody(),
                issue.getState(),
                issue.getStateReason() != null ? issue.getStateReason().name() : null,
                issue.getHtmlUrl(),
                issue.isPullRequest(),
                RepositoryRef.from(issue.getRepository()),
                issue.getAuthor() != null ? issue.getAuthor().getId() : null,
                issue.getCreatedAt(),
                issue.getUpdatedAt(),
                issue.getClosedAt()
            );
        }
    }

    // ========================================================================
    // Pull Request Event Payload
    // ========================================================================

    /**
     * Immutable snapshot of a PullRequest for event handling.
     */
    public record PullRequestData(
        @NonNull Long id,
        int number,
        @NonNull String title,
        @Nullable String body,
        @NonNull PullRequest.State state,
        boolean isDraft,
        boolean isMerged,
        int additions,
        int deletions,
        int changedFiles,
        @NonNull String htmlUrl,
        @NonNull RepositoryRef repository,
        @Nullable Long authorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable Instant closedAt,
        @Nullable Instant mergedAt,
        @Nullable Long mergedById
    ) {
        public static PullRequestData from(PullRequest pr) {
            return new PullRequestData(
                pr.getId(),
                pr.getNumber(),
                pr.getTitle(),
                pr.getBody(),
                pr.getState(),
                pr.isDraft(),
                pr.isMerged(),
                pr.getAdditions(),
                pr.getDeletions(),
                pr.getChangedFiles(),
                pr.getHtmlUrl(),
                RepositoryRef.from(pr.getRepository()),
                pr.getAuthor() != null ? pr.getAuthor().getId() : null,
                pr.getCreatedAt(),
                pr.getUpdatedAt(),
                pr.getClosedAt(),
                pr.getMergedAt(),
                pr.getMergedBy() != null ? pr.getMergedBy().getId() : null
            );
        }
    }

    // ========================================================================
    // Label Event Payload
    // ========================================================================

    /**
     * Immutable snapshot of a Label for event handling.
     */
    public record LabelData(
        @NonNull Long id,
        @NonNull String name,
        @NonNull String color,
        @Nullable String description
    ) {
        public static LabelData from(Label label) {
            return new LabelData(label.getId(), label.getName(), label.getColor(), label.getDescription());
        }
    }

    // ========================================================================
    // Milestone Event Payload
    // ========================================================================

    /**
     * Immutable snapshot of a Milestone for event handling.
     */
    public record MilestoneData(
        @NonNull Long id,
        int number,
        @NonNull String title,
        @Nullable String description,
        @NonNull Milestone.State state
    ) {
        public static MilestoneData from(Milestone milestone) {
            return new MilestoneData(
                milestone.getId(),
                milestone.getNumber(),
                milestone.getTitle(),
                milestone.getDescription(),
                milestone.getState()
            );
        }
    }

    // ========================================================================
    // Issue Type Event Payload
    // ========================================================================

    public record IssueTypeData(@NonNull String id, @NonNull String name, @Nullable String description) {
        public static IssueTypeData from(IssueType issueType) {
            return new IssueTypeData(issueType.getId(), issueType.getName(), issueType.getDescription());
        }
    }

    // ========================================================================
    // Comment Event Payload
    // ========================================================================

    public record CommentData(
        @NonNull Long id,
        @Nullable String body,
        @NonNull String htmlUrl,
        @Nullable Long authorId,
        @Nullable Instant createdAt,
        @Nullable Long issueId,
        @Nullable Long repositoryId
    ) {
        public static CommentData from(IssueComment comment) {
            return new CommentData(
                comment.getId(),
                comment.getBody(),
                comment.getHtmlUrl(),
                comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                comment.getCreatedAt(),
                comment.getIssue() != null ? comment.getIssue().getId() : null,
                comment.getIssue() != null && comment.getIssue().getRepository() != null
                    ? comment.getIssue().getRepository().getId()
                    : null
            );
        }
    }

    // ========================================================================
    // Pull Request Review Event Payload
    // ========================================================================

    public record ReviewData(
        @NonNull Long id,
        @Nullable String body,
        @NonNull PullRequestReview.State state,
        boolean isDismissed,
        @NonNull String htmlUrl,
        @Nullable Long authorId,
        @NonNull Long pullRequestId,
        @Nullable Instant submittedAt,
        @Nullable Long repositoryId
    ) {
        public static Optional<ReviewData> from(PullRequestReview review) {
            PullRequest pr = review.getPullRequest();
            if (pr == null) {
                log.debug(
                    "Cannot create ReviewData: pullRequest is null for reviewId={}",
                    review.getId()
                );
                return Optional.empty();
            }
            return Optional.of(
                new ReviewData(
                    review.getId(),
                    review.getBody(),
                    review.getState(),
                    review.isDismissed(),
                    review.getHtmlUrl(),
                    review.getAuthor() != null ? review.getAuthor().getId() : null,
                    pr.getId(),
                    review.getSubmittedAt(),
                    pr.getRepository() != null ? pr.getRepository().getId() : null
                )
            );
        }
    }

    // ========================================================================
    // Pull Request Review Comment Event Payload
    // ========================================================================

    public record ReviewCommentData(
        @NonNull Long id,
        @NonNull String body,
        @NonNull String path,
        int line,
        @NonNull String htmlUrl,
        @Nullable Long reviewId,
        @Nullable Long authorId,
        @Nullable Instant createdAt,
        @Nullable Long pullRequestId,
        @Nullable Long repositoryId
    ) {
        public static ReviewCommentData from(PullRequestReviewComment comment) {
            PullRequest pr = comment.getReview() != null
                ? comment.getReview().getPullRequest()
                : comment.getPullRequest();
            return new ReviewCommentData(
                comment.getId(),
                comment.getBody(),
                comment.getPath(),
                comment.getLine(),
                comment.getHtmlUrl(),
                comment.getReview() != null ? comment.getReview().getId() : null,
                comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                comment.getCreatedAt(),
                pr != null ? pr.getId() : null,
                pr != null && pr.getRepository() != null ? pr.getRepository().getId() : null
            );
        }
    }

    // ========================================================================
    // Pull Request Review Thread Event Payload
    // ========================================================================

    public record ReviewThreadData(
        @NonNull Long id,
        @NonNull PullRequestReviewThread.State state,
        @Nullable String path,
        @Nullable Integer line,
        @NonNull Long pullRequestId
    ) {
        public static Optional<ReviewThreadData> from(PullRequestReviewThread thread) {
            PullRequest pr = thread.getPullRequest();
            if (pr == null) {
                log.debug(
                    "Cannot create ReviewThreadData: pullRequest is null for threadId={}",
                    thread.getId()
                );
                return Optional.empty();
            }
            return Optional.of(
                new ReviewThreadData(
                    thread.getId(),
                    thread.getState(),
                    thread.getPath(),
                    thread.getLine(),
                    pr.getId()
                )
            );
        }
    }

    // ========================================================================
    // Team Event Payload
    // ========================================================================

    public record TeamData(
        @NonNull Long id,
        @NonNull String name,
        @Nullable String description,
        @Nullable String organization,
        @Nullable String htmlUrl
    ) {
        public static TeamData from(Team team) {
            return new TeamData(
                team.getId(),
                team.getName(),
                team.getDescription(),
                team.getOrganization(),
                team.getHtmlUrl()
            );
        }
    }

    // ========================================================================
    // User Event Payload
    // ========================================================================

    public record UserData(@NonNull Long id, @NonNull String login, @Nullable String name, @Nullable String avatarUrl) {
        public static UserData from(User user) {
            return new UserData(user.getId(), user.getLogin(), user.getName(), user.getAvatarUrl());
        }
    }
}
