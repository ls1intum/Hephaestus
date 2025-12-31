package de.tum.in.www1.hephaestus.gitprovider.common.events;

import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Type-safe domain events with sealed hierarchies per entity type.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>No JPA entities</b> - Events use {@link EventPayload} DTOs to avoid
 *       LazyInitializationException in async handlers</li>
 *   <li><b>Immutable</b> - All events are Java records</li>
 *   <li><b>Sealed hierarchies</b> - Enable exhaustive pattern matching</li>
 *   <li><b>Event metadata</b> - All events carry {@link EventContext} with
 *       timestamps, correlation IDs, and source information</li>
 * </ul>
 *
 * <h2>Usage</h2>
 * <pre>{@code
 * // Type-safe event handling with pattern matching
 * @EventListener
 * public void onPullRequestEvent(DomainEvent.PullRequestEvent event) {
 *     switch (event) {
 *         case PullRequestCreated e -> handleCreated(e.pullRequest());
 *         case PullRequestMerged e -> handleMerged(e.pullRequest());
 *         // Compiler ensures all cases are handled
 *     }
 * }
 *
 * // Async-safe - no LazyInitializationException risk
 * @Async
 * @TransactionalEventListener(phase = AFTER_COMMIT)
 * public void onPullRequestCreated(DomainEvent.PullRequestCreated event) {
 *     var pr = event.pullRequest();  // EventPayload.PullRequestData, not JPA entity
 *     log.info("PR #{} created: {}", pr.number(), pr.title());
 * }
 * }</pre>
 */
public final class DomainEvent {

    private DomainEvent() {}

    // ========================================================================
    // Common base interfaces
    // ========================================================================

    /** Marker for all domain events. */
    public sealed interface Event
        permits
            IssueEvent,
            PullRequestEvent,
            LabelEvent,
            MilestoneEvent,
            CommentEvent,
            ReviewEvent,
            ReviewCommentEvent,
            ReviewThreadEvent,
            TeamEvent {}

    /** Events that carry context information. */
    public interface ContextualEvent {
        EventContext context();
    }

    // ========================================================================
    // Issue Events
    // ========================================================================

    /** All issue-related events. Subscribe to handle any issue event. */
    public sealed interface IssueEvent
        extends Event
        permits
            IssueCreated,
            IssueUpdated,
            IssueClosed,
            IssueReopened,
            IssueLabeled,
            IssueUnlabeled,
            IssueTyped,
            IssueUntyped {
        EventPayload.IssueData issue();
    }

    public record IssueCreated(EventPayload.IssueData issue, EventContext context) implements
        IssueEvent, ContextualEvent {}

    public record IssueUpdated(EventPayload.IssueData issue, Set<String> changedFields, EventContext context) implements
        IssueEvent, ContextualEvent {}

    public record IssueClosed(
        EventPayload.IssueData issue,
        @Nullable String stateReason,
        EventContext context
    ) implements IssueEvent, ContextualEvent {}

    public record IssueReopened(EventPayload.IssueData issue, EventContext context) implements
        IssueEvent, ContextualEvent {}

    /** Deleted event is separate - entity no longer exists, only ID available. */
    public record IssueDeleted(Long issueId, EventContext context) implements ContextualEvent {}

    public record IssueLabeled(
        EventPayload.IssueData issue,
        EventPayload.LabelData label,
        EventContext context
    ) implements IssueEvent, ContextualEvent {}

    public record IssueUnlabeled(
        EventPayload.IssueData issue,
        EventPayload.LabelData label,
        EventContext context
    ) implements IssueEvent, ContextualEvent {}

    public record IssueTyped(
        EventPayload.IssueData issue,
        EventPayload.IssueTypeData issueType,
        EventContext context
    ) implements IssueEvent, ContextualEvent {}

    public record IssueUntyped(
        EventPayload.IssueData issue,
        @Nullable EventPayload.IssueTypeData previousType,
        EventContext context
    ) implements IssueEvent, ContextualEvent {}

    // ========================================================================
    // Pull Request Events
    // ========================================================================

    /** All pull request-related events. Subscribe to handle any PR event. */
    public sealed interface PullRequestEvent
        extends Event
        permits
            PullRequestCreated,
            PullRequestUpdated,
            PullRequestClosed,
            PullRequestMerged,
            PullRequestReopened,
            PullRequestLabeled,
            PullRequestUnlabeled,
            PullRequestReady,
            PullRequestDrafted,
            PullRequestSynchronized {
        EventPayload.PullRequestData pullRequest();
    }

    public record PullRequestCreated(EventPayload.PullRequestData pullRequest, EventContext context) implements
        PullRequestEvent, ContextualEvent {}

    public record PullRequestUpdated(
        EventPayload.PullRequestData pullRequest,
        Set<String> changedFields,
        EventContext context
    ) implements PullRequestEvent, ContextualEvent {}

    public record PullRequestClosed(
        EventPayload.PullRequestData pullRequest,
        boolean wasMerged,
        EventContext context
    ) implements PullRequestEvent, ContextualEvent {}

    public record PullRequestMerged(EventPayload.PullRequestData pullRequest, EventContext context) implements
        PullRequestEvent, ContextualEvent {}

    public record PullRequestReopened(EventPayload.PullRequestData pullRequest, EventContext context) implements
        PullRequestEvent, ContextualEvent {}

    public record PullRequestLabeled(
        EventPayload.PullRequestData pullRequest,
        EventPayload.LabelData label,
        EventContext context
    ) implements PullRequestEvent, ContextualEvent {}

    public record PullRequestUnlabeled(
        EventPayload.PullRequestData pullRequest,
        EventPayload.LabelData label,
        EventContext context
    ) implements PullRequestEvent, ContextualEvent {}

    public record PullRequestReady(EventPayload.PullRequestData pullRequest, EventContext context) implements
        PullRequestEvent, ContextualEvent {}

    public record PullRequestDrafted(EventPayload.PullRequestData pullRequest, EventContext context) implements
        PullRequestEvent, ContextualEvent {}

    public record PullRequestSynchronized(EventPayload.PullRequestData pullRequest, EventContext context) implements
        PullRequestEvent, ContextualEvent {}

    // ========================================================================
    // Label Events
    // ========================================================================

    /** All label-related events. */
    public sealed interface LabelEvent extends Event permits LabelCreated, LabelUpdated, LabelDeleted {
        Long repositoryId();
        Long workspaceId();
    }

    public record LabelCreated(EventPayload.LabelData label, Long workspaceId, Long repositoryId) implements
        LabelEvent {}

    public record LabelUpdated(EventPayload.LabelData label, Long workspaceId, Long repositoryId) implements
        LabelEvent {}

    public record LabelDeleted(Long labelId, String labelName, Long workspaceId, Long repositoryId) implements
        LabelEvent {}

    // ========================================================================
    // Milestone Events
    // ========================================================================

    /** All milestone-related events. */
    public sealed interface MilestoneEvent extends Event permits MilestoneCreated, MilestoneUpdated, MilestoneDeleted {
        Long repositoryId();
        Long workspaceId();
    }

    public record MilestoneCreated(EventPayload.MilestoneData milestone, Long workspaceId, Long repositoryId) implements
        MilestoneEvent {}

    public record MilestoneUpdated(EventPayload.MilestoneData milestone, Long workspaceId, Long repositoryId) implements
        MilestoneEvent {}

    public record MilestoneDeleted(Long milestoneId, String title, Long workspaceId, Long repositoryId) implements
        MilestoneEvent {}

    // ========================================================================
    // Comment Events
    // ========================================================================

    /** All comment-related events. */
    public sealed interface CommentEvent extends Event permits CommentCreated, CommentUpdated, CommentDeleted {
        Long issueId();
    }

    public record CommentCreated(EventPayload.CommentData comment, Long issueId, EventContext context) implements
        CommentEvent, ContextualEvent {}

    public record CommentUpdated(
        EventPayload.CommentData comment,
        Long issueId,
        Set<String> changedFields,
        EventContext context
    ) implements CommentEvent, ContextualEvent {}

    public record CommentDeleted(Long commentId, Long issueId, EventContext context) implements
        CommentEvent, ContextualEvent {}

    // ========================================================================
    // Pull Request Review Events
    // ========================================================================

    public sealed interface ReviewEvent extends Event permits ReviewSubmitted, ReviewEdited, ReviewDismissed {
        EventPayload.ReviewData review();
    }

    public record ReviewSubmitted(EventPayload.ReviewData review, EventContext context) implements
        ReviewEvent, ContextualEvent {}

    public record ReviewEdited(
        EventPayload.ReviewData review,
        Set<String> changedFields,
        EventContext context
    ) implements ReviewEvent, ContextualEvent {}

    public record ReviewDismissed(EventPayload.ReviewData review, EventContext context) implements
        ReviewEvent, ContextualEvent {}

    // ========================================================================
    // Pull Request Review Comment Events
    // ========================================================================

    public sealed interface ReviewCommentEvent
        extends Event
        permits ReviewCommentCreated, ReviewCommentEdited, ReviewCommentDeleted {
        Long pullRequestId();
    }

    public record ReviewCommentCreated(
        EventPayload.ReviewCommentData comment,
        Long pullRequestId,
        EventContext context
    ) implements ReviewCommentEvent, ContextualEvent {}

    public record ReviewCommentEdited(
        EventPayload.ReviewCommentData comment,
        Long pullRequestId,
        Set<String> changedFields,
        EventContext context
    ) implements ReviewCommentEvent, ContextualEvent {}

    public record ReviewCommentDeleted(Long commentId, Long pullRequestId, EventContext context) implements
        ReviewCommentEvent, ContextualEvent {}

    // ========================================================================
    // Pull Request Review Thread Events
    // ========================================================================

    public sealed interface ReviewThreadEvent extends Event permits ReviewThreadResolved, ReviewThreadUnresolved {
        EventPayload.ReviewThreadData thread();
    }

    public record ReviewThreadResolved(EventPayload.ReviewThreadData thread, EventContext context) implements
        ReviewThreadEvent, ContextualEvent {}

    public record ReviewThreadUnresolved(EventPayload.ReviewThreadData thread, EventContext context) implements
        ReviewThreadEvent, ContextualEvent {}

    // ========================================================================
    // Team Events
    // ========================================================================

    public sealed interface TeamEvent extends Event permits TeamCreated, TeamUpdated, TeamDeleted {
        Long teamId();
    }

    public record TeamCreated(EventPayload.TeamData team, EventContext context) implements TeamEvent, ContextualEvent {
        @Override
        public Long teamId() {
            return team.id();
        }
    }

    public record TeamUpdated(EventPayload.TeamData team, Set<String> changedFields, EventContext context) implements
        TeamEvent, ContextualEvent {
        @Override
        public Long teamId() {
            return team.id();
        }
    }

    public record TeamDeleted(Long teamId, String teamName, EventContext context) implements
        TeamEvent, ContextualEvent {
        @Override
        public Long teamId() {
            return teamId;
        }
    }
}
