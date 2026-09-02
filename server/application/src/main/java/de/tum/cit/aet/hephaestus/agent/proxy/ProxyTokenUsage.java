package de.tum.cit.aet.hephaestus.agent.proxy;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;

/**
 * Normalized token buckets for one proxied response.
 *
 * @param billableInputTokens prompt tokens MINUS cache reads and writes, because OpenAI-compatible
 *     providers report both as details of the inclusive prompt total
 * @param reasoningTokens already counted inside {@link #outputTokens}; reported, never priced twice
 */
public record ProxyTokenUsage(
        int billableInputTokens, int outputTokens, int reasoningTokens, int cacheReadTokens, int cacheWriteTokens) {
    /** @return {@code null} when no usage block is present */
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
        int cacheWrite;
        if (responsesProtocol) {
            input = count(usage, "input_tokens");
            output = count(usage, "output_tokens");
            cacheRead = count(usage.path("input_tokens_details"), "cached_tokens");
            cacheWrite = cacheWriteTokens(usage.path("input_tokens_details"));
            reasoning = count(usage.path("output_tokens_details"), "reasoning_tokens");
        } else {
            input = count(usage, "prompt_tokens");
            output = count(usage, "completion_tokens");
            cacheRead = count(usage.path("prompt_tokens_details"), "cached_tokens");
            cacheWrite = cacheWriteTokens(usage.path("prompt_tokens_details"));
            reasoning = count(usage.path("completion_tokens_details"), "reasoning_tokens");
        }
        if ((long) cacheRead + cacheWrite > input) {
            throw new IllegalArgumentException("Cache token details exceed input tokens");
        }
        return new ProxyTokenUsage(input - cacheRead - cacheWrite, output, reasoning, cacheRead, cacheWrite);
    }

    private static int cacheWriteTokens(JsonNode details) {
        Integer standard = optionalCount(details, "cache_write_tokens");
        Integer logos = optionalCount(details, "created_cache_tokens");
        if (standard != null && logos != null && !standard.equals(logos)) {
            throw new IllegalArgumentException("Conflicting cache-write token details");
        }
        return standard != null ? standard : logos != null ? logos : 0;
    }

    private static int count(JsonNode owner, String field) {
        Integer value = optionalCount(owner, field);
        return value != null ? value : 0;
    }

    private static @Nullable Integer optionalCount(JsonNode owner, String field) {
        JsonNode value = owner.get(field);
        if (value == null || value.isNull()) {
            return null;
        }
        if (!value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 0) {
            throw new IllegalArgumentException("Invalid token count: " + field);
        }
        return value.intValue();
    }
}
