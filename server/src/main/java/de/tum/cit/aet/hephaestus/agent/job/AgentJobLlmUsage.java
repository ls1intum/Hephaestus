package de.tum.cit.aet.hephaestus.agent.job;

/**
 * A job's accumulated LLM token totals (#1368), read from the {@code agent_job} row as a projection.
 * The proxy adds one call's tokens per non-streaming forward; the crash/cancel accounting paths read
 * this back to bill the calls a job actually made before it terminated abnormally.
 */
public record AgentJobLlmUsage(
    int totalCalls,
    int inputTokens,
    int outputTokens,
    int reasoningTokens,
    int cacheReadTokens,
    int cacheWriteTokens
) {
    /** True when at least one proxied call was recorded — i.e. there is real spend to bill. */
    public boolean hasBillableUsage() {
        return (
            totalCalls > 0 &&
            (inputTokens > 0 || outputTokens > 0 || reasoningTokens > 0 || cacheReadTokens > 0 || cacheWriteTokens > 0)
        );
    }
}
