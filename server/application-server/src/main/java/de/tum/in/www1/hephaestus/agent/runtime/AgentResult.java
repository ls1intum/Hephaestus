package de.tum.in.www1.hephaestus.agent.runtime;

import java.util.Map;
import org.springframework.lang.Nullable;

/** Parsed result of an agent execution. Shared across Pi-based agent variants. */
public record AgentResult(boolean success, Map<String, Object> output, @Nullable LlmUsage usage) {
    public AgentResult {
        output = output != null ? Map.copyOf(output) : Map.of();
    }

    public AgentResult(boolean success, Map<String, Object> output) {
        this(success, output, null);
    }

    /** LLM usage. */
    public record LlmUsage(
        @Nullable String model,
        @Nullable Integer inputTokens,
        @Nullable Integer outputTokens,
        @Nullable Integer reasoningTokens,
        @Nullable Integer cacheReadTokens,
        @Nullable Integer cacheWriteTokens,
        @Nullable Double costUsd,
        int totalCalls
    ) {}
}
