package de.tum.cit.aet.hephaestus.agent.job;

/**
 * A job's LLM token totals as the proxy accumulated them onto the {@code agent_job} row, one
 * non-streaming forward at a time. This is what the crash/cancel accounting paths bill from.
 */
public record AgentJobLlmUsage(
        int totalCalls,
        int inputTokens,
        int outputTokens,
        int reasoningTokens,
        int cacheReadTokens,
        int cacheWriteTokens) {
    /** A call alone is not spend: there must also be a non-zero token bucket to price. */
    public boolean hasBillableUsage() {
        return (totalCalls > 0
                && (inputTokens > 0
                        || outputTokens > 0
                        || reasoningTokens > 0
                        || cacheReadTokens > 0
                        || cacheWriteTokens > 0));
    }
}
