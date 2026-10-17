package de.tum.cit.aet.hephaestus.agent.job;

/**
 * A job's LLM token totals as the proxy accumulated them onto the {@code review_job} row, one
 * non-streaming forward at a time. This is what the crash/cancel accounting paths bill from.
 */
public record ReviewJobLlmUsage(
        int totalCalls,
        long inputTokens,
        long outputTokens,
        long reasoningTokens,
        long cacheReadTokens,
        long cacheWriteTokens) {
    /** The proxy increments the call count only for reported usage, including a reported zero. */
    public boolean hasReportedUsage() {
        return totalCalls > 0;
    }
}
