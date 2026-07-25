package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Thrown at an LLM enforcement point when the monthly cap of whoever pays for the call is reached
 * (#1368). The message is user-facing — it surfaces verbatim on the mentor channel (web SSE error
 * chunk / Slack message).
 *
 * <p>The copy names the admin who can actually lift the pause, which differs by purse: a workspace
 * admin owns the workspace's own-provider cap, while the shared-model budget is the host's. Telling
 * a workspace admin to "ask an instance admin" about a cap they set themselves is the failure this
 * distinction exists to avoid.
 */
public class LlmBudgetExhaustedException extends RuntimeException {

    public LlmBudgetExhaustedException(Long workspaceId, FundingSource fundingSource) {
        // No workspace id in the text: this surfaces verbatim in a chat reply, where an internal
        // identifier is noise to the reader and means nothing they can act on.
        super(message(fundingSource));
    }

    private static String message(FundingSource fundingSource) {
        return fundingSource == FundingSource.WORKSPACE
            ? "This workspace's monthly budget for its own AI provider is used up. Work on that provider " +
              "is paused until the next month starts or a workspace admin raises the cap."
            : "The monthly shared-model AI budget for this workspace is used up. Work on shared models is " +
              "paused until the next month starts or an instance admin raises the budget. Work on the " +
              "workspace's own provider is unaffected.";
    }
}
