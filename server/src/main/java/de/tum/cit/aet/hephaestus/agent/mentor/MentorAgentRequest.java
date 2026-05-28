package de.tum.cit.aet.hephaestus.agent.mentor;

/**
 * Routing identity for {@link MentorPiAdapter#buildSandboxSpec}. Only the (workspaceId,
 * contributorId) pair influences the sandbox spec — the registry keys on it. Per-turn
 * payload (threadId, user message, replay history) is carried separately by
 * {@code MentorChatService} and reaches the runner via the JSON-RPC channel, not the spec.
 */
public record MentorAgentRequest(long workspaceId, long contributorId) {
    public MentorAgentRequest {
        if (workspaceId <= 0) {
            throw new IllegalArgumentException("workspaceId must be positive, got " + workspaceId);
        }
        if (contributorId <= 0) {
            throw new IllegalArgumentException("contributorId must be positive, got " + contributorId);
        }
    }
}
