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
    /** The proxy increments the call count only for reported usage, including a reported zero. */
    public boolean hasReportedUsage() {
        return totalCalls > 0;
    }
}
