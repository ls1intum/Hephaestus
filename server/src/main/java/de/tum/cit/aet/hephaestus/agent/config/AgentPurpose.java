package de.tum.cit.aet.hephaestus.agent.config;

/**
 * The two things a workspace runs an LLM for (#1368). Each purpose has at most one
 * {@link WorkspaceAgentBinding} per workspace — "what model runs detection" and "what model runs the
 * mentor" are the whole configuration surface, so the binding is keyed by {@code (workspace, purpose)}
 * rather than by a free-form config name.
 *
 * <p>Practice detection covers all three detection job types (pull-request, issue, and conversation
 * review) — they share one binding. A new purpose value is the only change needed if a future job
 * type ever warrants its own model.
 */
public enum AgentPurpose {
    /** Pull-request, issue, and conversation practice review. */
    PRACTICE_DETECTION,
    /** Interactive mentor turns (web SSE and Slack). */
    MENTOR,
}
