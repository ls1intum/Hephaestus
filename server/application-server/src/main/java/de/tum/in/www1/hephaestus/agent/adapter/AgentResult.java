package de.tum.in.www1.hephaestus.agent.adapter;

import java.util.Map;

/**
 * Parsed result of an agent execution.
 *
 * @param success whether the agent completed its task successfully
 * @param output  structured output from the agent (agent-specific keys)
 */
public record AgentResult(boolean success, Map<String, Object> output) {
    public AgentResult {
        output = output != null ? Map.copyOf(output) : Map.of();
    }
}
