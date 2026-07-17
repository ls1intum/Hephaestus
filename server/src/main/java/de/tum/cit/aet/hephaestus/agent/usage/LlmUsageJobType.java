package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;

/**
 * Job-type dimension of the unified LLM usage ledger. Mirrors {@link AgentJobType} for
 * sandboxed detection jobs and adds {@code MENTOR_TURN} for interactive mentor turns
 * (web SSE + Slack), so one enum spans every LLM spend source.
 */
public enum LlmUsageJobType {
    PULL_REQUEST_REVIEW,
    ISSUE_REVIEW,
    CONVERSATION_REVIEW,
    MENTOR_TURN;

    /** Ledger job type for a detection job. Exhaustive so a new {@link AgentJobType} must map here. */
    public static LlmUsageJobType from(AgentJobType jobType) {
        return switch (jobType) {
            case PULL_REQUEST_REVIEW -> PULL_REQUEST_REVIEW;
            case ISSUE_REVIEW -> ISSUE_REVIEW;
            case CONVERSATION_REVIEW -> CONVERSATION_REVIEW;
        };
    }
}
