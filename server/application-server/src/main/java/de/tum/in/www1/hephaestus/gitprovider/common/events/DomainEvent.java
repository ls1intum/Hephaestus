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
            TeamEvent,
            DiscussionEvent,
            CommitEvent {}

    /** Events that carry context information. */
    public interface ContextualEvent {
        EventContext context();
    }

    // ========================================================================
    // Issue Events
    // ========================================================================

    /** All issue-related events. Subscribe to handle any issue event. */
    public sealed interface IssueEvent
        extends Event, ContextualEvent
        permits
            IssueCreated,
            IssueUpdated,
            IssueClosed,
            IssueReopened,
            IssueDeleted,
            IssueLabeled,
            IssueUnlabeled,
            IssueTyped,
            IssueUntyped
    {
        EventPayload.IssueData issue();
    }

    public record IssueCreated(EventPayload.IssueData issue, EventContext context) implements IssueEvent {}

    public record IssueUpdated(
        EventPayload.IssueData issue,
        Set<String> changedFields,
        EventContext context
    ) implements IssueEvent {}

    public record IssueClosed(
        EventPayload.IssueData issue,
        @Nullable String stateReason,
        EventContext context
    ) implements IssueEvent {}

    public record IssueReopened(EventPayload.IssueData issue, EventContext context) implements IssueEvent {}

    /** Deleted event is separate - entity no longer exists, only ID available. */
    public record IssueDeleted(Long issueId, EventContext context) implements IssueEvent {
        @Override
        public EventPayload.IssueData issue() {
            return null; // Entity no longer exists
        }
    }

    public record IssueLabeled(
        EventPayload.IssueData issue,
        EventPayload.LabelData label,
        EventContext context
    ) implements IssueEvent {}

    public record IssueUnlabeled(
        EventPayload.IssueData issue,
        EventPayload.LabelData label,
        EventContext context
    ) implements IssueEvent {}

    public record IssueTyped(
        EventPayload.IssueData issue,
        EventPayload.IssueTypeData issueType,
        EventContext context
    ) implements IssueEvent {}

    public record IssueUntyped(
        EventPayload.IssueData issue,
        @Nullable EventPayload.IssueTypeData previousType,
        EventContext context
    ) implements IssueEvent {}

    // ========================================================================
    // Pull Request Events
    // ========================================================================

    /** All pull request-related events. Subscribe to handle any PR event. */
    public sealed interface PullRequestEvent
        extends Event, ContextualEvent
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
            PullRequestSynchronized
    {
        EventPayload.PullRequestData pullRequest();
    }

    public record PullRequestCreated(
        EventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestUpdated(
        EventPayload.PullRequestData pullRequest,
        Set<String> changedFields,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestClosed(
        EventPayload.PullRequestData pullRequest,
        boolean wasMerged,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestMerged(
        EventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestReopened(
        EventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestLabeled(
        EventPayload.PullRequestData pullRequest,
        EventPayload.LabelData label,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestUnlabeled(
        EventPayload.PullRequestData pullRequest,
        EventPayload.LabelData label,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestReady(
        EventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestDrafted(
        EventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestSynchronized(
        EventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    // ========================================================================
    // Label Events
    // ========================================================================

    /** All label-related events. */
    public sealed interface LabelEvent
        extends Event, ContextualEvent
        permits LabelCreated, LabelUpdated, LabelDeleted {}

    public record LabelCreated(EventPayload.LabelData label, EventContext context) implements LabelEvent {}

    public record LabelUpdated(EventPayload.LabelData label, EventContext context) implements LabelEvent {}

    public record LabelDeleted(Long labelId, String labelName, EventContext context) implements LabelEvent {}

    // ========================================================================
    // Milestone Events
    // ========================================================================

    /** All milestone-related events. */
    public sealed interface MilestoneEvent
        extends Event, ContextualEvent
        permits MilestoneCreated, MilestoneUpdated, MilestoneDeleted {}

    public record MilestoneCreated(
        EventPayload.MilestoneData milestone,
        EventContext context
    ) implements MilestoneEvent {}

    public record MilestoneUpdated(
        EventPayload.MilestoneData milestone,
        EventContext context
    ) implements MilestoneEvent {}

    public record MilestoneDeleted(Long milestoneId, String title, EventContext context) implements MilestoneEvent {}

    // ========================================================================
    // Comment Events
    // ========================================================================

    /** All comment-related events. */
    public sealed interface CommentEvent
        extends Event, ContextualEvent
        permits CommentCreated, CommentUpdated, CommentDeleted
    {
        Long issueId();
    }

    public record CommentCreated(
        EventPayload.CommentData comment,
        Long issueId,
        EventContext context
    ) implements CommentEvent {}

    public record CommentUpdated(
        EventPayload.CommentData comment,
        Long issueId,
        Set<String> changedFields,
        EventContext context
    ) implements CommentEvent {}

    public record CommentDeleted(Long commentId, Long issueId, EventContext context) implements CommentEvent {}

    // ========================================================================
    // Pull Request Review Events
    // ========================================================================

    public sealed interface ReviewEvent
        extends Event, ContextualEvent
        permits ReviewSubmitted, ReviewEdited, ReviewDismissed
    {
        EventPayload.ReviewData review();
    }

    public record ReviewSubmitted(EventPayload.ReviewData review, EventContext context) implements ReviewEvent {}

    public record ReviewEdited(
        EventPayload.ReviewData review,
        Set<String> changedFields,
        EventContext context
    ) implements ReviewEvent {}

    public record ReviewDismissed(EventPayload.ReviewData review, EventContext context) implements ReviewEvent {}

    // ========================================================================
    // Pull Request Review Comment Events
    // ========================================================================

    public sealed interface ReviewCommentEvent
        extends Event, ContextualEvent
        permits ReviewCommentCreated, ReviewCommentEdited, ReviewCommentDeleted
    {
        Long pullRequestId();
    }

    public record ReviewCommentCreated(
        EventPayload.ReviewCommentData comment,
        Long pullRequestId,
        EventContext context
    ) implements ReviewCommentEvent {}

    public record ReviewCommentEdited(
        EventPayload.ReviewCommentData comment,
        Long pullRequestId,
        Set<String> changedFields,
        EventContext context
    ) implements ReviewCommentEvent {}

    public record ReviewCommentDeleted(
        Long commentId,
        Long pullRequestId,
        EventContext context
    ) implements ReviewCommentEvent {}

    // ========================================================================
    // Pull Request Review Thread Events
    // ========================================================================

    public sealed interface ReviewThreadEvent
        extends Event, ContextualEvent
        permits ReviewThreadResolved, ReviewThreadUnresolved
    {
        EventPayload.ReviewThreadData thread();
    }

    public record ReviewThreadResolved(
        EventPayload.ReviewThreadData thread,
        EventContext context
    ) implements ReviewThreadEvent {}

    public record ReviewThreadUnresolved(
        EventPayload.ReviewThreadData thread,
        EventContext context
    ) implements ReviewThreadEvent {}

    // ========================================================================
    // Team Events
    // ========================================================================

    public sealed interface TeamEvent extends Event, ContextualEvent permits TeamCreated, TeamUpdated, TeamDeleted {
        Long teamId();
    }

    public record TeamCreated(EventPayload.TeamData team, EventContext context) implements TeamEvent {
        @Override
        public Long teamId() {
            return team.id();
        }
    }

    public record TeamUpdated(
        EventPayload.TeamData team,
        Set<String> changedFields,
        EventContext context
    ) implements TeamEvent {
        @Override
        public Long teamId() {
            return team.id();
        }
    }

    public record TeamDeleted(Long teamId, String teamName, EventContext context) implements TeamEvent {
        @Override
        public Long teamId() {
            return teamId;
        }
    }

    // ========================================================================
    // Discussion Events
    // ========================================================================

    /** All discussion-related events. Subscribe to handle any discussion event. */
    public sealed interface DiscussionEvent
        extends Event, ContextualEvent
        permits
            DiscussionCreated,
            DiscussionUpdated,
            DiscussionClosed,
            DiscussionReopened,
            DiscussionAnswered,
            DiscussionDeleted
    {
        EventPayload.DiscussionData discussion();
    }

    public record DiscussionCreated(
        EventPayload.DiscussionData discussion,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionUpdated(
        EventPayload.DiscussionData discussion,
        Set<String> changedFields,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionClosed(
        EventPayload.DiscussionData discussion,
        @Nullable String stateReason,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionReopened(
        EventPayload.DiscussionData discussion,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionAnswered(
        EventPayload.DiscussionData discussion,
        Long answerCommentId,
        EventContext context
    ) implements DiscussionEvent {}

    /** Deleted event is separate - entity no longer exists, only ID available. */
    public record DiscussionDeleted(Long discussionId, EventContext context) implements DiscussionEvent {
        @Override
        public EventPayload.DiscussionData discussion() {
            return null; // Entity no longer exists
        }
    }

    // ========================================================================
    // Commit Events
    // ========================================================================

    /** All commit-related events. Subscribe to handle any commit event. */
    public sealed interface CommitEvent extends Event, ContextualEvent permits CommitCreated {
        EventPayload.CommitData commit();
    }

    public record CommitCreated(EventPayload.CommitData commit, EventContext context) implements CommitEvent {}
}
