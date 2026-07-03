package de.tum.cit.aet.hephaestus.agent;

/**
 * Discriminator for {@code AgentJob} that dispatches to the appropriate {@code JobTypeHandler}.
 *
 * <p>Each value corresponds to a handler implementation that knows how to prepare the Docker
 * volume, parse output, and deliver results. Add new values as new handler types are built.
 */
public enum AgentJobType {
    PULL_REQUEST_REVIEW,
    ISSUE_REVIEW,
    /**
     * Detection over a settled Slack conversation thread. Repo-less: the case context is the
     * thread's human turns (materialised as {@code inputs/context/conversation_thread.json}), with no
     * clone, no diff, and no SCM source mount. Handled by {@code ConversationReviewHandler}.
     */
    CONVERSATION_REVIEW,
}
