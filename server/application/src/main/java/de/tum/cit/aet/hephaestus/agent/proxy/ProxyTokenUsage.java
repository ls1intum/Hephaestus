package de.tum.cit.aet.hephaestus.agent.proxy;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * One proxied call's token counts, read off an OpenAI-compatible {@code usage} block — the single
 * parser for both API shapes and both transports, so the buffered and streamed billing paths cannot
 * drift apart on which bucket a token belongs in.
 *
 * @param billableInputTokens prompt tokens MINUS the cached ones, because upstream reports the prompt
 *     count inclusive of cache reads and a cache read must not be billed at both rates
 * @param reasoningTokens already counted inside {@link #outputTokens}; reported, never priced twice
 */
public record ProxyTokenUsage(int billableInputTokens, int outputTokens, int reasoningTokens, int cacheReadTokens) {
    /**
     * @param usageOwner the node that CONTAINS a {@code usage} field
     * @return {@code null} when there is no usage block to read, which is not an error: the caller
     *     records nothing rather than guessing
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
            reasoning =
                    usage.path("output_tokens_details").path("reasoning_tokens").asInt(0);
        } else {
            input = usage.path("prompt_tokens").asInt(0);
            output = usage.path("completion_tokens").asInt(0);
            cacheRead =
                    usage.path("prompt_tokens_details").path("cached_tokens").asInt(0);
            reasoning = usage.path("completion_tokens_details")
                    .path("reasoning_tokens")
                    .asInt(0);
        }
        return new ProxyTokenUsage(Math.max(0, input - cacheRead), output, reasoning, cacheRead);
    }
}
