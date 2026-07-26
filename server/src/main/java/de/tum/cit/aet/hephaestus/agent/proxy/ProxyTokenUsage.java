package de.tum.cit.aet.hephaestus.agent.proxy;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One proxied call's token counts, read off an OpenAI-compatible {@code usage} block.
 *
 * <p>The single parser for both API shapes and both transports: a buffered response body, and the
 * usage frame the streaming tap lifts out of an SSE stream. Having exactly one reader is the point —
 * a second copy would be free to disagree about which bucket cache reads belong in, and the two
 * billing paths would drift apart silently.
 *
 * @param billableInputTokens prompt tokens MINUS the cached ones. Upstream reports the prompt count
 *     inclusive of cache reads; the ledger's input bucket is the non-cached remainder so a cache read
 *     is not billed at both the input rate and the cache-read rate.
 * @param reasoningTokens already counted inside {@link #outputTokens}; carried separately only so the
 *     row can report it, never added again when pricing.
 */
public record ProxyTokenUsage(int billableInputTokens, int outputTokens, int reasoningTokens, int cacheReadTokens) {
    /**
     * Read the {@code usage} object out of {@code usageOwner}.
     *
     * @param usageOwner the node that CONTAINS a {@code usage} field — a buffered chat-completions or
     *     responses body, or (for a stream) the {@code response} envelope of the terminal event.
     * @param responsesProtocol true for the {@code /responses} token names, false for chat-completions
     * @return {@code null} when there is no usage block to read, which is not an error: a provider may
     *     omit it, and the caller records nothing rather than guessing.
     */
    static @Nullable ProxyTokenUsage from(@Nullable JsonNode usageOwner, boolean responsesProtocol) {
        if (usageOwner == null) {
            return null;
        }
        JsonNode usage = usageOwner.get("usage");
        if (usage == null || !usage.isObject()) {
            return null;
        }
        int input;
        int output;
        int reasoning;
        int cacheRead;
        if (responsesProtocol) {
            input = usage.path("input_tokens").asInt(0);
            output = usage.path("output_tokens").asInt(0);
            cacheRead = usage.path("input_tokens_details").path("cached_tokens").asInt(0);
            reasoning = usage.path("output_tokens_details").path("reasoning_tokens").asInt(0);
        } else {
            input = usage.path("prompt_tokens").asInt(0);
            output = usage.path("completion_tokens").asInt(0);
            cacheRead = usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
            reasoning = usage.path("completion_tokens_details").path("reasoning_tokens").asInt(0);
        }
        return new ProxyTokenUsage(Math.max(0, input - cacheRead), output, reasoning, cacheRead);
    }
}
