package de.tum.cit.aet.hephaestus.integration.core.events;

import de.tum.cit.aet.hephaestus.integration.scm.domain.commit.Commit;
import de.tum.cit.aet.hephaestus.integration.scm.domain.discussion.Discussion;
import de.tum.cit.aet.hephaestus.integration.scm.domain.discussioncomment.DiscussionComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issue.Issue;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuecomment.IssueComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.issuetype.IssueType;
import de.tum.cit.aet.hephaestus.integration.scm.domain.label.Label;
import de.tum.cit.aet.hephaestus.integration.scm.domain.milestone.Milestone;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequest.PullRequest;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreview.PullRequestReview;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.cit.aet.hephaestus.integration.scm.domain.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.cit.aet.hephaestus.integration.scm.domain.team.Team;
import de.tum.cit.aet.hephaestus.integration.scm.domain.user.User;
import java.time.Instant;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Immutable event payload DTOs for SCM domain events. These records are safe for
 * async handling — they capture entity state at publish time, so subscribers
 * never trigger {@code LazyInitializationException}.
 *
 * <p>Imports are restricted to the vendor-neutral SCM domain layer
 * ({@code integration.scm.domain.*}) by {@link
 * de.tum.cit.aet.hephaestus.architecture.IntegrationCoreVendorNeutralityTest}.
 * Vendor-specific payloads (e.g. GitHub Projects V2 — no GitLab equivalent)
 * live in vendor packages: see {@code integration.scm.github.events.GitHubProjectEventPayload}.
 */
public final class ScmEventPayload {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ScmEventPayload.class);

    private ScmEventPayload() {}

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

    public record IssueTypeData(@NonNull String id, @NonNull String name, @Nullable String description) {
        public static IssueTypeData from(IssueType issueType) {
            return new IssueTypeData(issueType.getId(), issueType.getName(), issueType.getDescription());
        }
    }

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
                log.debug("Cannot create ReviewData: pullRequest is null for reviewId={}", review.getId());
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
            PullRequest pr =
                comment.getReview() != null ? comment.getReview().getPullRequest() : comment.getPullRequest();
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
                log.debug("Cannot create ReviewThreadData: pullRequest is null for threadId={}", thread.getId());
                return Optional.empty();
            }
            return Optional.of(
                new ReviewThreadData(thread.getId(), thread.getState(), thread.getPath(), thread.getLine(), pr.getId())
            );
        }
    }

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

    public record UserData(@NonNull Long id, @NonNull String login, @Nullable String name, @Nullable String avatarUrl) {
        public static UserData from(User user) {
            return new UserData(user.getId(), user.getLogin(), user.getName(), user.getAvatarUrl());
        }
    }

    public record CommitData(
        @NonNull Long id,
        @NonNull String sha,
        @NonNull String message,
        @NonNull Instant authoredAt,
        int additions,
        int deletions,
        int changedFiles,
        @Nullable String htmlUrl,
        @Nullable Long authorId,
        @Nullable Long committerId,
        @NonNull Long repositoryId
    ) {
        public static CommitData from(Commit commit) {
            return new CommitData(
                commit.getId(),
                commit.getSha(),
                commit.getMessage(),
                commit.getAuthoredAt(),
                commit.getAdditions(),
                commit.getDeletions(),
                commit.getChangedFiles(),
                commit.getHtmlUrl(),
                commit.getAuthor() != null ? commit.getAuthor().getId() : null,
                commit.getCommitter() != null ? commit.getCommitter().getId() : null,
                commit.getRepository().getId()
            );
        }
    }

    public record DiscussionData(
        @NonNull Long id,
        int number,
        @NonNull String title,
        @Nullable String body,
        @NonNull Discussion.State state,
        @Nullable String stateReason,
        boolean isLocked,
        int commentsCount,
        @NonNull String htmlUrl,
        @NonNull RepositoryRef repository,
        @Nullable Long authorId,
        @Nullable String categoryName,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable Instant closedAt,
        @Nullable Instant answerChosenAt
    ) {
        public static DiscussionData from(Discussion discussion) {
            return new DiscussionData(
                discussion.getId(),
                discussion.getNumber(),
                discussion.getTitle(),
                discussion.getBody(),
                discussion.getState(),
                discussion.getStateReason() != null ? discussion.getStateReason().name() : null,
                discussion.isLocked(),
                discussion.getCommentsCount(),
                discussion.getHtmlUrl(),
                RepositoryRef.from(discussion.getRepository()),
                discussion.getAuthor() != null ? discussion.getAuthor().getId() : null,
                discussion.getCategory() != null ? discussion.getCategory().getName() : null,
                discussion.getCreatedAt(),
                discussion.getUpdatedAt(),
                discussion.getClosedAt(),
                discussion.getAnswerChosenAt()
            );
        }
    }

    public record DiscussionCommentData(
        @NonNull Long id,
        @Nullable String body,
        @NonNull String htmlUrl,
        boolean isAnswer,
        @Nullable Long authorId,
        @Nullable Instant createdAt,
        @Nullable Long discussionId,
        @Nullable Long repositoryId
    ) {
        public static DiscussionCommentData from(DiscussionComment comment) {
            return new DiscussionCommentData(
                comment.getId(),
                comment.getBody(),
                comment.getHtmlUrl(),
                comment.isAnswer(),
                comment.getAuthor() != null ? comment.getAuthor().getId() : null,
                comment.getCreatedAt(),
                comment.getDiscussion() != null ? comment.getDiscussion().getId() : null,
                comment.getDiscussion() != null && comment.getDiscussion().getRepository() != null
                    ? comment.getDiscussion().getRepository().getId()
                    : null
            );
        }
    }
}
