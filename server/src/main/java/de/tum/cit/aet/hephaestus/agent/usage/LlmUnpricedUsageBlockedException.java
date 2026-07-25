package de.tum.cit.aet.hephaestus.agent.usage;

/**
 * Thrown at an LLM enforcement point when a cap is set but this month's spend against it cannot be
 * verified — at least one event funded from that purse has no resolvable price, so the ledger cannot
 * rule out the cap already being exceeded (#1368). A cap you cannot verify is not a cap.
 *
 * <p>Like {@link LlmBudgetExhaustedException}, the copy points at whoever can clear the blind spot:
 * a missing price on the workspace's own model is the workspace admin's to set, while a shared
 * model's price is the host's.
 */
public class LlmUnpricedUsageBlockedException extends RuntimeException {

    public LlmUnpricedUsageBlockedException(Long workspaceId, FundingSource fundingSource) {
        super(message(fundingSource));
    }

    private static String message(FundingSource fundingSource) {
        return fundingSource == FundingSource.WORKSPACE
            ? "Some usage on this workspace's own AI provider has no price set, so spending can't be " +
              "checked against the cap. A workspace admin can add the model's price, or remove the cap."
            : "Some shared-model usage in this workspace has no price set, so spending can't be checked " +
              "against the budget. An instance admin can add the model's price, or remove the budget.";
    }
}
