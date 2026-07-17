package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Thrown at an LLM enforcement point when the workspace's monthly budget cap is reached
 * (#1368). The message is user-facing — surfaces verbatim on the mentor channel (web SSE
 * error chunk / Slack message).
 */
public class LlmBudgetExhaustedException extends RuntimeException {

    public LlmBudgetExhaustedException(Long workspaceId) {
        super(
            "The monthly AI budget for this workspace is used up. Mentor and practice detection " +
                "are paused until the next month starts or an instance admin raises the budget. (workspace " +
                workspaceId +
                ")"
        );
    }
}
