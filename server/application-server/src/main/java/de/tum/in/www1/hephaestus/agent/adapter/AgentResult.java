package de.tum.in.www1.hephaestus.agent.adapter;

import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Parsed result of an agent execution.
 *
 * @param success whether the agent completed its task successfully
 * @param output  structured output from the agent (agent-specific keys)
 * @param usage   LLM usage reported by the agent itself (null if not available)
 */
public record AgentResult(boolean success, Map<String, Object> output, @Nullable LlmUsage usage) {
    public AgentResult {
        output = output != null ? Map.copyOf(output) : Map.of();
    }

    /** Convenience constructor without usage. */
    public AgentResult(boolean success, Map<String, Object> output) {
        this(success, output, null);
    }

    /**
     * LLM usage as reported by the agent runtime.
     */
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
