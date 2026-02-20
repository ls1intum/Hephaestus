package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.commit.Commit;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuecomment.IssueComment;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.project.Project;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectItem;
import de.tum.in.www1.hephaestus.gitprovider.project.ProjectStatusUpdate;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreview.PullRequestReview;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewcomment.PullRequestReviewComment;
import de.tum.in.www1.hephaestus.gitprovider.pullrequestreviewthread.PullRequestReviewThread;
import de.tum.in.www1.hephaestus.gitprovider.team.Team;
import de.tum.in.www1.hephaestus.gitprovider.user.User;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.lang.Nullable;

/**
 * Immutable event payload DTOs for domain events.
 * These records are safe for async handling - no lazy-loaded relationships.
 */
@Slf4j
public final class EventPayload {

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
                log.debug("Cannot create ReviewThreadData: pullRequest is null for threadId={}", thread.getId());
                return Optional.empty();
            }
            return Optional.of(
                new ReviewThreadData(thread.getId(), thread.getState(), thread.getPath(), thread.getLine(), pr.getId())
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

    // ========================================================================
    // Project Event Payload (GitHub Projects V2)
    // ========================================================================

    /**
     * Immutable snapshot of a Project for event handling.
     *
     * @param actorId The user who performed the action (e.g., closed, reopened, updated the project).
     *                This is distinct from creatorId which is the original project creator.
     *                For webhook events, this comes from the sender field.
     *                For sync operations, this may be null.
     */
    public record ProjectData(
        @NonNull Long id,
        int number,
        @NonNull String title,
        @Nullable String shortDescription,
        boolean closed,
        boolean isPublic,
        @NonNull String url,
        @NonNull Project.OwnerType ownerType,
        @NonNull Long ownerId,
        @Nullable Long creatorId,
        @Nullable Long actorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt,
        @Nullable Instant closedAt
    ) {
        /**
         * Creates ProjectData from a Project entity with no actor specified.
         * Use this for sync operations where the actor is unknown.
         */
        public static ProjectData from(Project project) {
            return from(project, null);
        }

        /**
         * Creates ProjectData from a Project entity with the specified actor.
         * Use this for webhook events where the sender is known.
         *
         * @param project the project entity
         * @param actorId the ID of the user who performed the action (from webhook sender)
         */
        public static ProjectData from(Project project, @Nullable Long actorId) {
            return new ProjectData(
                project.getId(),
                project.getNumber(),
                project.getTitle() != null ? project.getTitle() : "Untitled",
                project.getShortDescription(),
                project.isClosed(),
                project.isPublic(),
                project.getUrl() != null ? project.getUrl() : "",
                project.getOwnerType(),
                project.getOwnerId(),
                project.getCreator() != null ? project.getCreator().getId() : null,
                actorId,
                project.getCreatedAt(),
                project.getUpdatedAt(),
                project.getClosedAt()
            );
        }
    }

    // ========================================================================
    // Project Item Event Payload (GitHub Projects V2)
    // ========================================================================

    /**
     * Immutable snapshot of a ProjectItem for event handling.
     *
     * @param actorId The user who performed the action (e.g., created, archived, moved the item).
     *                For webhook events, this comes from the sender field.
     *                For sync operations, this may be null.
     */
    public record ProjectItemData(
        @NonNull Long id,
        @NonNull String nodeId,
        @NonNull Long projectId,
        @NonNull ProjectItem.ContentType contentType,
        @Nullable Long issueId,
        boolean archived,
        @Nullable Long actorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt
    ) {
        /**
         * Creates ProjectItemData from a ProjectItem entity with no actor specified.
         * Use this for sync operations where the actor is unknown.
         */
        public static ProjectItemData from(ProjectItem item) {
            return from(item, null);
        }

        /**
         * Creates ProjectItemData from a ProjectItem entity with the specified actor.
         * Use this for webhook events where the sender is known.
         *
         * @param item the project item entity
         * @param actorId the ID of the user who performed the action (from webhook sender)
         */
        public static ProjectItemData from(ProjectItem item, @Nullable Long actorId) {
            return new ProjectItemData(
                item.getId(),
                item.getNodeId() != null ? item.getNodeId() : "",
                item.getProject().getId(),
                item.getContentType(),
                item.getIssue() != null ? item.getIssue().getId() : null,
                item.isArchived(),
                actorId,
                item.getCreatedAt(),
                item.getUpdatedAt()
            );
        }
    }

    // ========================================================================
    // Project Status Update Event Payload
    // ========================================================================

    /**
     * Immutable snapshot of a ProjectStatusUpdate for event handling.
     */
    public record ProjectStatusUpdateData(
        @NonNull Long id,
        @NonNull String nodeId,
        @NonNull Long projectId,
        @Nullable String body,
        @Nullable LocalDate startDate,
        @Nullable LocalDate targetDate,
        @Nullable ProjectStatusUpdate.Status status,
        @Nullable Long creatorId,
        @Nullable Instant createdAt,
        @Nullable Instant updatedAt
    ) {
        public static ProjectStatusUpdateData from(ProjectStatusUpdate update) {
            return new ProjectStatusUpdateData(
                update.getId(),
                update.getNodeId() != null ? update.getNodeId() : "",
                update.getProject().getId(),
                update.getBody(),
                update.getStartDate(),
                update.getTargetDate(),
                update.getStatus(),
                update.getCreator() != null ? update.getCreator().getId() : null,
                update.getCreatedAt(),
                update.getUpdatedAt()
            );
        }
    }

    // ========================================================================
    // Commit Event Payload
    // ========================================================================

    /**
     * Immutable snapshot of a Commit for event handling.
     */
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
}
