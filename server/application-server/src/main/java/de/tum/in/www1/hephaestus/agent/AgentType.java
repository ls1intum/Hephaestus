package de.tum.in.www1.hephaestus.agent;

/**
 * Supported coding agent runtimes.
 *
 * <p>Each type implies a set of compatible {@link LlmProvider}s:
 * <ul>
 *   <li>{@link #CLAUDE_CODE} — requires {@link LlmProvider#ANTHROPIC}</li>
 *   <li>{@link #CODEX} — requires {@link LlmProvider#OPENAI}</li>
 *   <li>{@link #OPENCODE} — any provider</li>
 * </ul>
 */
public enum AgentType {
    CLAUDE_CODE,
    CODEX,
    OPENCODE,
}
