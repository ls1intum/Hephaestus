package de.tum.cit.aet.hephaestus.mentor;

/**
 * A mentor turn's accumulated LLM token totals, projected from the {@code chat_message} row rather
 * than read off a loaded entity, so it reflects every proxy call that has committed.
 */
public record MentorTurnLlmUsage(
        int totalCalls, long inputTokens, long outputTokens, long reasoningTokens, long cacheReadTokens) {
    public static final MentorTurnLlmUsage NONE = new MentorTurnLlmUsage(0, 0, 0, 0, 0);

    /** True when at least one proxied call was recorded — i.e. there is real spend to bill. */
    public boolean hasBillableUsage() {
        return totalCalls > 0 && (inputTokens > 0 || outputTokens > 0 || cacheReadTokens > 0);
    }
}
