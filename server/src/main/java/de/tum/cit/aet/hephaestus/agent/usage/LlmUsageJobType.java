package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.agent.AgentJobType;

/** {@link AgentJobType} plus {@code MENTOR_TURN}, so one enum spans every LLM spend source. */
public enum LlmUsageJobType {
    PULL_REQUEST_REVIEW,
    ISSUE_REVIEW,
    CONVERSATION_REVIEW,
    MENTOR_TURN;

    public static LlmUsageJobType from(AgentJobType jobType) {
        return switch (jobType) {
            case PULL_REQUEST_REVIEW -> PULL_REQUEST_REVIEW;
            case ISSUE_REVIEW -> ISSUE_REVIEW;
            case CONVERSATION_REVIEW -> CONVERSATION_REVIEW;
        };
    }
}
