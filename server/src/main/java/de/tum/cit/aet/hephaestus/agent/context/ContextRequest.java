package de.tum.cit.aet.hephaestus.agent.context;

import de.tum.cit.aet.hephaestus.agent.job.AgentJob;
import java.util.Objects;
import java.util.UUID;

/**
 * Polymorphic request handed to {@link WorkspaceContextBuilder}. Providers narrow on the
 * variant via a sealed switch — no {@code instanceof} chains in the orchestrator.
 *
 * <p>The universal {@code AgentJob job()} accessor that previously lived on this interface
 * was removed because synchronous mentor chat ({@link MentorChatRequest}) has no
 * {@link AgentJob}. Per-variant accessors carry the variant-specific identity.
 */
public sealed interface ContextRequest
    permits ContextRequest.PracticeReviewRequest, ContextRequest.IssueReviewRequest, ContextRequest.MentorChatRequest
{
    /**
     * Build the materialised PR-review context: metadata, comments, diff, contributor history.
     * Carries the {@link AgentJob} the practice runner will execute.
     */
    record PracticeReviewRequest(AgentJob job) implements ContextRequest {
        public PracticeReviewRequest {
            Objects.requireNonNull(job, "job must not be null");
        }
    }

    /**
     * Build the materialised issue-detection context: issue metadata, the comment thread, and the
     * state-transition timeline — NO diff. Carries the {@link AgentJob} the practice runner executes.
     */
    record IssueReviewRequest(AgentJob job) implements ContextRequest {
        public IssueReviewRequest {
            Objects.requireNonNull(job, "job must not be null");
        }
    }

    /**
     * Build the materialised mentor-chat context: user activity, workspace aspect, practice
     * catalog, findings history. There is no {@link AgentJob} — mentor chat is synchronous
     * and runs against a long-lived interactive sandbox keyed by {@code (workspaceId, contributorId)}.
     *
     * @param workspaceId   workspace scoping for every aspect provider's queries
     * @param contributorId the active user (sometimes called {@code userId}) the aspects describe
     * @param threadId      conversation thread the request originated from (used by providers
     *                      that need per-thread cache keys)
     */
    record MentorChatRequest(long workspaceId, long contributorId, UUID threadId) implements ContextRequest {
        public MentorChatRequest {
            Objects.requireNonNull(threadId, "threadId must not be null");
            if (workspaceId <= 0) {
                throw new IllegalArgumentException("workspaceId must be positive, got " + workspaceId);
            }
            if (contributorId <= 0) {
                throw new IllegalArgumentException("contributorId must be positive, got " + contributorId);
            }
        }
    }
}
