package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.Set;

/**
 * Generic domain events for GitHub entities.
 * <p>
 * Listeners can filter by entity type using instanceof checks.
 */
public final class EntityEvents {

    private EntityEvents() {}

    /** Base interface for all entity events. */
    public interface EntityEvent {
        ProcessingContext context();
    }

    /** Published when an entity is created. */
    public record Created<E>(E entity, ProcessingContext context) implements EntityEvent {}

    /** Published when an entity is updated. */
    public record Updated<E>(E entity, Set<String> changedFields, ProcessingContext context) implements EntityEvent {}

    /** Published when a closeable entity is closed. */
    public record Closed<E>(E entity, String stateReason, ProcessingContext context) implements EntityEvent {}

    /** Published when an entity is deleted. */
    public record Deleted<E>(Long entityId, Class<E> entityType, ProcessingContext context) implements EntityEvent {}

    /** Published when a label is added to an entity. */
    public record Labeled<E>(E entity, Label label, ProcessingContext context) implements EntityEvent {}

    /** Published when a label is removed from an entity. */
    public record Unlabeled<E>(E entity, Label label, ProcessingContext context) implements EntityEvent {}

    /** Published when an issue type is assigned. */
    public record Typed(Issue issue, IssueType issueType, ProcessingContext context) implements EntityEvent {}

    /** Published when an issue type is removed. */
    public record Untyped(Issue issue, IssueType previousType, ProcessingContext context) implements EntityEvent {}

    /** Published when a PR is merged. */
    public record PullRequestMerged(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    /** Published when a PR becomes ready for review. */
    public record PullRequestReady(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    /** Published when a PR is converted to draft. */
    public record PullRequestDrafted(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    /** Published when commits are pushed to a PR. */
    public record PullRequestSynchronized(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    /** Published when a label is processed (created or updated). */
    public record LabelProcessed(Label label, boolean isNew, Long workspaceId, Long repositoryId) {}

    /** Published when a label is deleted. */
    public record LabelDeleted(Long labelId, String labelName, Long workspaceId, Long repositoryId) {}

    /** Published when a milestone is processed (created or updated). */
    public record MilestoneProcessed(Milestone milestone, boolean isNew, Long workspaceId, Long repositoryId) {}

    /** Published when a milestone is deleted. */
    public record MilestoneDeleted(Long milestoneId, String title, Long workspaceId, Long repositoryId) {}
}
