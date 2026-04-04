package de.tum.in.www1.hephaestus.agent;

/**
 * Supported coding agent runtimes.
 *
 * <p>Each type implies a set of compatible {@link LlmProvider}s:
 * <ul>
 *   <li>{@link #CLAUDE_CODE} — requires {@link LlmProvider#ANTHROPIC}</li>
 *   <li>{@link #OPENCODE} — any provider</li>
 *   <li>{@link #PI} — any provider (multi-provider via Pi coding agent)</li>
 * </ul>
 */
public enum AgentType {
    CLAUDE_CODE,
    OPENCODE,
    PI,
}
