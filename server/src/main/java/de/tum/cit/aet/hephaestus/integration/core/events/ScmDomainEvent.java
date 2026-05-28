package de.tum.cit.aet.hephaestus.integration.core.events;

import java.util.Set;
import org.springframework.lang.Nullable;

/**
 * Type-safe domain events with sealed hierarchies per entity type.
 *
 * <h2>Design Principles</h2>
 * <ul>
 *   <li><b>No JPA entities</b> - Events use {@link ScmEventPayload} DTOs to avoid
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
 * public void onPullRequestEvent(ScmDomainEvent.PullRequestEvent event) {
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
 * public void onPullRequestCreated(ScmDomainEvent.PullRequestCreated event) {
 *     var pr = event.pullRequest();  // ScmEventPayload.PullRequestData, not JPA entity
 *     log.info("PR #{} created: {}", pr.number(), pr.title());
 * }
 * }</pre>
 */
public final class ScmDomainEvent {

    private ScmDomainEvent() {}

    /**
     * Canonical trigger event names used for practice matching.
     * <p>
     * These must match exactly the strings stored in the {@code trigger_events} JSONB column
     * of the {@code practice} table. Practices are matched by comparing their trigger events
     * against these constants in {@code PracticeReviewDetectionGate.findMatchingPractices()}.
     */
    public static final class TriggerEventNames {

        public static final String PULL_REQUEST_CREATED = "PullRequestCreated";
        public static final String PULL_REQUEST_READY = "PullRequestReady";
        public static final String PULL_REQUEST_SYNCHRONIZED = "PullRequestSynchronized";
        public static final String REVIEW_SUBMITTED = "ReviewSubmitted";

        private TriggerEventNames() {}
    }

    // ========================================================================
    // Common base interfaces
    // ========================================================================

    /**
     * Marker for vendor-neutral SCM domain events. GitHub Projects V2 events
     * live in {@code integration.scm.github.events.GitHubProjectEvent} — they
     * are GitHub-only (no GitLab equivalent) and listeners pattern-match on
     * those hierarchies directly.
     */
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
            CommitEvent,
            DiscussionEvent,
            DiscussionCommentEvent {}

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
        ScmEventPayload.IssueData issue();
    }

    public record IssueCreated(ScmEventPayload.IssueData issue, EventContext context) implements IssueEvent {}

    public record IssueUpdated(
        ScmEventPayload.IssueData issue,
        Set<String> changedFields,
        EventContext context
    ) implements IssueEvent {}

    public record IssueClosed(
        ScmEventPayload.IssueData issue,
        @Nullable String stateReason,
        EventContext context
    ) implements IssueEvent {}

    public record IssueReopened(ScmEventPayload.IssueData issue, EventContext context) implements IssueEvent {}

    /** Deleted event is separate - entity no longer exists, only ID available. */
    public record IssueDeleted(Long issueId, EventContext context) implements IssueEvent {
        @Override
        public ScmEventPayload.IssueData issue() {
            return null; // Entity no longer exists
        }
    }

    public record IssueLabeled(
        ScmEventPayload.IssueData issue,
        ScmEventPayload.LabelData label,
        EventContext context
    ) implements IssueEvent {}

    public record IssueUnlabeled(
        ScmEventPayload.IssueData issue,
        ScmEventPayload.LabelData label,
        EventContext context
    ) implements IssueEvent {}

    public record IssueTyped(
        ScmEventPayload.IssueData issue,
        ScmEventPayload.IssueTypeData issueType,
        EventContext context
    ) implements IssueEvent {}

    public record IssueUntyped(
        ScmEventPayload.IssueData issue,
        @Nullable ScmEventPayload.IssueTypeData previousType,
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
        ScmEventPayload.PullRequestData pullRequest();
    }

    public record PullRequestCreated(
        ScmEventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestUpdated(
        ScmEventPayload.PullRequestData pullRequest,
        Set<String> changedFields,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestClosed(
        ScmEventPayload.PullRequestData pullRequest,
        boolean wasMerged,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestMerged(
        ScmEventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestReopened(
        ScmEventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestLabeled(
        ScmEventPayload.PullRequestData pullRequest,
        ScmEventPayload.LabelData label,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestUnlabeled(
        ScmEventPayload.PullRequestData pullRequest,
        ScmEventPayload.LabelData label,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestReady(
        ScmEventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestDrafted(
        ScmEventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    public record PullRequestSynchronized(
        ScmEventPayload.PullRequestData pullRequest,
        EventContext context
    ) implements PullRequestEvent {}

    // ========================================================================
    // Label Events
    // ========================================================================

    /** All label-related events. */
    public sealed interface LabelEvent
        extends Event, ContextualEvent
        permits LabelCreated, LabelUpdated, LabelDeleted {}

    public record LabelCreated(ScmEventPayload.LabelData label, EventContext context) implements LabelEvent {}

    public record LabelUpdated(ScmEventPayload.LabelData label, EventContext context) implements LabelEvent {}

    public record LabelDeleted(Long labelId, String labelName, EventContext context) implements LabelEvent {}

    // ========================================================================
    // Milestone Events
    // ========================================================================

    /** All milestone-related events. */
    public sealed interface MilestoneEvent
        extends Event, ContextualEvent
        permits MilestoneCreated, MilestoneUpdated, MilestoneDeleted {}

    public record MilestoneCreated(
        ScmEventPayload.MilestoneData milestone,
        EventContext context
    ) implements MilestoneEvent {}

    public record MilestoneUpdated(
        ScmEventPayload.MilestoneData milestone,
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
        ScmEventPayload.CommentData comment,
        Long issueId,
        EventContext context
    ) implements CommentEvent {}

    public record CommentUpdated(
        ScmEventPayload.CommentData comment,
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
        ScmEventPayload.ReviewData review();
    }

    public record ReviewSubmitted(ScmEventPayload.ReviewData review, EventContext context) implements ReviewEvent {}

    public record ReviewEdited(
        ScmEventPayload.ReviewData review,
        Set<String> changedFields,
        EventContext context
    ) implements ReviewEvent {}

    public record ReviewDismissed(ScmEventPayload.ReviewData review, EventContext context) implements ReviewEvent {}

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
        ScmEventPayload.ReviewCommentData comment,
        Long pullRequestId,
        EventContext context
    ) implements ReviewCommentEvent {}

    public record ReviewCommentEdited(
        ScmEventPayload.ReviewCommentData comment,
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
        ScmEventPayload.ReviewThreadData thread();
    }

    public record ReviewThreadResolved(
        ScmEventPayload.ReviewThreadData thread,
        EventContext context
    ) implements ReviewThreadEvent {}

    public record ReviewThreadUnresolved(
        ScmEventPayload.ReviewThreadData thread,
        EventContext context
    ) implements ReviewThreadEvent {}

    // ========================================================================
    // Team Events
    // ========================================================================

    public sealed interface TeamEvent extends Event, ContextualEvent permits TeamCreated, TeamUpdated, TeamDeleted {
        Long teamId();
    }

    public record TeamCreated(ScmEventPayload.TeamData team, EventContext context) implements TeamEvent {
        @Override
        public Long teamId() {
            return team.id();
        }
    }

    public record TeamUpdated(
        ScmEventPayload.TeamData team,
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
    // GitHub Projects V2 events moved to
    // integration.scm.github.events.GitHubProjectEvent — GitHub-only feature,
    // pinned out of core by IntegrationCoreVendorNeutralityTest.
    // ========================================================================

    // ========================================================================
    // Commit Events
    // ========================================================================

    /** All commit-related events. */
    public sealed interface CommitEvent extends Event, ContextualEvent permits CommitCreated, CommitAuthorsReconciled {
        @Nullable
        ScmEventPayload.CommitData commit();
    }

    public record CommitCreated(ScmEventPayload.CommitData commit, EventContext context) implements CommitEvent {}

    /**
     * Bulk reconciliation marker emitted after commit author identities have been
     * resolved for a repository (via email lookup, provider user API, or server-side
     * author harvest). Carries no per-commit payload; downstream consumers use it
     * as a signal that previously unresolved {@code actor_id} references across the
     * repository's ledger can now be re-evaluated.
     */
    public record CommitAuthorsReconciled(Long repositoryId, EventContext context) implements CommitEvent {
        @Override
        public ScmEventPayload.CommitData commit() {
            return null;
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
        ScmEventPayload.DiscussionData discussion();
    }

    public record DiscussionCreated(
        ScmEventPayload.DiscussionData discussion,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionUpdated(
        ScmEventPayload.DiscussionData discussion,
        Set<String> changedFields,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionClosed(
        ScmEventPayload.DiscussionData discussion,
        @Nullable String stateReason,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionReopened(
        ScmEventPayload.DiscussionData discussion,
        EventContext context
    ) implements DiscussionEvent {}

    public record DiscussionAnswered(
        ScmEventPayload.DiscussionData discussion,
        Long answerCommentId,
        EventContext context
    ) implements DiscussionEvent {}

    /** Deleted event is separate - entity no longer exists, only ID available. */
    public record DiscussionDeleted(Long discussionId, EventContext context) implements DiscussionEvent {
        @Override
        public ScmEventPayload.DiscussionData discussion() {
            return null; // Entity no longer exists
        }
    }

    // ========================================================================
    // Discussion Comment Events
    // ========================================================================

    /** All discussion comment-related events. Subscribe to handle any discussion comment event. */
    public sealed interface DiscussionCommentEvent
        extends Event, ContextualEvent
        permits DiscussionCommentCreated, DiscussionCommentEdited, DiscussionCommentDeleted
    {
        Long discussionId();
    }

    public record DiscussionCommentCreated(
        ScmEventPayload.DiscussionCommentData comment,
        Long discussionId,
        EventContext context
    ) implements DiscussionCommentEvent {}

    public record DiscussionCommentEdited(
        ScmEventPayload.DiscussionCommentData comment,
        Long discussionId,
        Set<String> changedFields,
        EventContext context
    ) implements DiscussionCommentEvent {}

    public record DiscussionCommentDeleted(
        Long commentId,
        Long discussionId,
        EventContext context
    ) implements DiscussionCommentEvent {}
}
