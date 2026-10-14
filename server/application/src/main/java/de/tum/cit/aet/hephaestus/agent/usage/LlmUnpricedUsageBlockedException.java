package de.tum.cit.aet.hephaestus.agent.usage;

import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import java.io.Serial;

/**
 * Thrown at an LLM enforcement point when a cap is set but this month's spend against it cannot be
 * verified — at least one record funded from that purse has no confirmed cost, so the ledger cannot
 * rule out the cap already being exceeded.
 *
 * <p>User-facing message, and not an HTTP exception, for the reasons on {@link LlmBudgetExhaustedException}.
 */
public class LlmUnpricedUsageBlockedException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

    public LlmUnpricedUsageBlockedException(UsageFundingSource fundingSource) {
        super(message(fundingSource));
    }

    private static String message(UsageFundingSource fundingSource) {
        return fundingSource == UsageFundingSource.WORKSPACE
                ? "Some usage has no price, so the server cannot check it against the cap. A workspace admin can "
                        + "add the price or remove the cap."
                : "Some usage has no price, so the server cannot check it against the budget. An instance admin "
                        + "can add the price or remove the budget.";
    }
}
