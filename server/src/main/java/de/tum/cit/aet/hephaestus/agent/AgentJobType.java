package de.tum.cit.aet.hephaestus.agent;

/**
 * Discriminator for {@code AgentJob} that dispatches to the appropriate {@code JobTypeHandler}.
 *
 * <p>Each value corresponds to a handler implementation that knows how to prepare the Docker
 * volume, parse output, and deliver results. Add new values as new handler types are built.
 */
public enum AgentJobType {
    /** Detection over a pull/merge request's diff, comments, and review state. */
    PULL_REQUEST_REVIEW,
    /** Detection over an issue's body, comment thread, and lifecycle state. */
    ISSUE_REVIEW,
    /** Detection over a settled Slack conversation thread. */
    CONVERSATION_REVIEW,
}
