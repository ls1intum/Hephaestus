package de.tum.cit.aet.hephaestus.mentor;

/**
 * A mentor turn's accumulated LLM token totals, read from the {@code chat_message} row as a
 * projection rather than off a loaded entity — so it reflects every proxy call that has COMMITTED,
 * including ones that landed after the caller loaded the message.
 *
 * <p>The mentor counterpart of {@code AgentJobLlmUsage}. Read by the accounting paths that have to
 * bill a turn whose runner reported nothing: {@code MentorTurnPersistence} on interrupt, and
 * {@code MentorInFlightReaper} for a turn whose worker died without either.
 */
public record MentorTurnLlmUsage(
    int totalCalls,
    long inputTokens,
    long outputTokens,
    long reasoningTokens,
    long cacheReadTokens
) {
    public static final MentorTurnLlmUsage NONE = new MentorTurnLlmUsage(0, 0, 0, 0, 0);

    /** True when at least one proxied call was recorded — i.e. there is real spend to bill. */
    public boolean hasBillableUsage() {
        return totalCalls > 0 && (inputTokens > 0 || outputTokens > 0 || cacheReadTokens > 0);
    }
}
