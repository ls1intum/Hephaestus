package de.tum.cit.aet.hephaestus.agent.usage;

import java.io.Serial;

/**
 * Thrown at an LLM enforcement point when a cap is set but this month's spend against it cannot be
 * verified — at least one event funded from that purse has no resolvable price, so the ledger cannot
 * rule out the cap already being exceeded. A cap you cannot verify is not a cap.
 *
 * <p>Like {@link LlmBudgetExhaustedException}, the copy points at whoever can clear the blind spot:
 * a missing price on the workspace's own model is the workspace admin's to set, while a shared
 * model's price is the host's.
 *
 * <p>It is also <b>not</b> an HTTP exception, for the reason spelled out on that sibling: thrown and
 * caught entirely inside a mentor turn, it surfaces as an error chunk on the already-open channel and
 * never unwinds to a controller, so it deliberately carries no {@code @ResponseStatus} and no
 * {@code @ExceptionHandler}.
 */
public class LlmUnpricedUsageBlockedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmUnpricedUsageBlockedException(FundingSource fundingSource) {
        super(message(fundingSource));
    }

    private static String message(FundingSource fundingSource) {
        return fundingSource == FundingSource.WORKSPACE
            ? "Some usage has no price, so it can't be checked against the cap. A workspace admin can " +
              "add the price or remove the cap."
            : "Some usage has no price, so it can't be checked against the budget. An instance admin " +
              "can add the price or remove the budget.";
    }
}
