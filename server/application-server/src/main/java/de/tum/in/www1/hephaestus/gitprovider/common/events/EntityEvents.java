package de.tum.in.www1.hephaestus.gitprovider.common.events;

import de.tum.in.www1.hephaestus.gitprovider.common.ProcessingContext;
import de.tum.in.www1.hephaestus.gitprovider.issue.Issue;
import de.tum.in.www1.hephaestus.gitprovider.issuetype.IssueType;
import de.tum.in.www1.hephaestus.gitprovider.label.Label;
import de.tum.in.www1.hephaestus.gitprovider.pullrequest.PullRequest;
import java.util.Set;

/**
 * Generic domain events for all GitHub entities.
 * <p>
 * This replaces the explosion of entity-specific event files
 * (IssueCreatedEvent, PullRequestCreatedEvent, CommentCreatedEvent, etc.)
 * with a small set of generic events that work across all entities.
 * <p>
 * <b>Design rationale:</b>
 * <ul>
 * <li>Reduces file count from 50+ to ~10 event types</li>
 * <li>Listeners can filter by entity type using instanceof or generics</li>
 * <li>Adding new entities doesn't require new event classes</li>
 * <li>Consistent event structure across the entire system</li>
 * </ul>
 */
public final class EntityEvents {

    private EntityEvents() {
        // Utility class
    }

    // ==================== Base Event Interface ====================

    /**
     * Base interface for all entity events.
     */
    public interface EntityEvent {
        ProcessingContext context();
    }

    // ==================== Generic Event Types ====================

    /**
     * Published when any entity is created.
     *
     * @param entity The created entity (Issue, PullRequest, Comment, etc.)
     */
    public record Created<E>(E entity, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when any entity is updated.
     *
     * @param entity        The updated entity
     * @param changedFields Which fields changed (for selective processing)
     */
    public record Updated<E>(E entity, Set<String> changedFields, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when any closeable entity is closed.
     *
     * @param entity      The closed entity
     * @param stateReason Optional reason (e.g., "completed", "not_planned")
     */
    public record Closed<E>(E entity, String stateReason, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when any entity is deleted.
     *
     * @param entityId   The ID of the deleted entity
     * @param entityType The class of the deleted entity
     */
    public record Deleted<E>(Long entityId, Class<E> entityType, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when a label is added to any labelable entity.
     *
     * @param entity The entity that was labeled
     * @param label  The label that was added
     */
    public record Labeled<E>(E entity, Label label, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when a label is removed from any labelable entity.
     *
     * @param entity The entity that was unlabeled
     * @param label  The label that was removed
     */
    public record Unlabeled<E>(E entity, Label label, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when an issue type is assigned. (GitHub-specific)
     */
    public record Typed(Issue issue, IssueType issueType, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when an issue type is removed. (GitHub-specific)
     */
    public record Untyped(Issue issue, IssueType previousType, ProcessingContext context) implements EntityEvent {}

    // ==================== Pull Request Specific Events ====================
    // These don't fit the generic pattern well, so they remain specific

    /**
     * Published when a PR is merged (closed + merged = true).
     */
    public record PullRequestMerged(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when a PR becomes ready for review.
     */
    public record PullRequestReady(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when a PR is converted to draft.
     */
    public record PullRequestDrafted(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    /**
     * Published when commits are pushed to a PR.
     */
    public record PullRequestSynchronized(PullRequest pullRequest, ProcessingContext context) implements EntityEvent {}

    // ==================== Label Specific Events ====================

    /**
     * Published when a label is processed (created or updated).
     *
     * @param label the processed label
     * @param isNew true if the label was newly created
     * @param workspaceId the workspace ID
     * @param repositoryId the repository ID
     */
    public record LabelProcessed(Label label, boolean isNew, Long workspaceId, Long repositoryId) {}

    /**
     * Published when a label is deleted.
     *
     * @param labelId the ID of the deleted label
     * @param labelName the name of the deleted label
     * @param workspaceId the workspace ID
     * @param repositoryId the repository ID
     */
    public record LabelDeleted(Long labelId, String labelName, Long workspaceId, Long repositoryId) {}

    // ==================== Milestone Specific Events ====================

    /**
     * Published when a milestone is processed (created or updated).
     */
    public record MilestoneProcessed(
        de.tum.in.www1.hephaestus.gitprovider.milestone.Milestone milestone,
        boolean isNew,
        Long workspaceId,
        Long repositoryId
    ) {}

    /**
     * Published when a milestone is deleted.
     */
    public record MilestoneDeleted(Long milestoneId, String title, Long workspaceId, Long repositoryId) {}
}
