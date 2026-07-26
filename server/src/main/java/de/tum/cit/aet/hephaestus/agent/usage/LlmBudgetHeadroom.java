package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A workspace's two caps and what the LEDGER says has been spent against each — the inputs of the cap
 * comparison, kept as numbers instead of a finished verdict.
 *
 * <p>{@link LlmBudgetDecision} answers "is this workspace blocked by what has been recorded?".
 * A ledger row is only written when an agent job or a mentor turn ENDS, so that question is blind to
 * a running execution's own spend. This record exists for the one caller that can see that spend —
 * the LLM proxy, which knows how many tokens the attempt on the other end of the request has already
 * consumed — and lets it add that amount before the comparison rather than after the money is gone.
 *
 * <p>A {@code null} budget is an uncapped purse: never blocked, and its spend is never even queried,
 * so the matching {@code spent} field is {@code null} too.
 *
 * <p><b>Why {@code hasUnpricedSpend} may read {@code false} on an exhausted purse.</b> The producer
 * skips the unpriced probe once the ledger alone already reaches the cap. That stays correct here
 * because in-flight spend is never negative: a purse that is EXHAUSTED on ledger spend alone is
 * EXHAUSTED for every in-flight amount, and EXHAUSTED already outranks UNVERIFIABLE.
 */
public record LlmBudgetHeadroom(
    @Nullable BigDecimal instanceSpentUsd,
    @Nullable BigDecimal instanceBudgetUsd,
    boolean instanceHasUnpricedSpend,
    @Nullable BigDecimal workspaceSpentUsd,
    @Nullable BigDecimal workspaceBudgetUsd,
    boolean workspaceHasUnpricedSpend
) {
    /** Neither purse is capped — the verdict for any in-flight amount is ALLOWED. */
    public static final LlmBudgetHeadroom UNCAPPED = new LlmBudgetHeadroom(null, null, false, null, null, false);

    /** The verdict on recorded spend alone — what every non-proxy gate asks. */
    public LlmBudgetDecision decide() {
        return decideWith(null, BigDecimal.ZERO);
    }

    /**
     * The verdict once {@code inFlightUsd} of not-yet-recorded spend is charged to {@code purse}.
     *
     * @param purse who pays for the in-flight spend; {@code null} means unattributable, and — exactly
     *     as {@link LlmBudgetDecision#decideFor} judges an unattributable call against BOTH caps — the
     *     amount is then charged to both purses rather than to neither. Fail-safe, not fail-open.
     * @param inFlightUsd already-incurred spend the ledger cannot see yet; never negative
     */
    public LlmBudgetDecision decideWith(@Nullable FundingSource purse, BigDecimal inFlightUsd) {
        return new LlmBudgetDecision(
            reason(
                instanceSpentUsd,
                instanceBudgetUsd,
                instanceHasUnpricedSpend,
                chargedTo(FundingSource.INSTANCE, purse, inFlightUsd)
            ),
            reason(
                workspaceSpentUsd,
                workspaceBudgetUsd,
                workspaceHasUnpricedSpend,
                chargedTo(FundingSource.WORKSPACE, purse, inFlightUsd)
            )
        );
    }

    private static BigDecimal chargedTo(FundingSource cap, @Nullable FundingSource purse, BigDecimal inFlightUsd) {
        if (inFlightUsd.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return purse == null || purse == cap ? inFlightUsd : BigDecimal.ZERO;
    }

    private static LlmBudgetBlockReason reason(
        @Nullable BigDecimal spentUsd,
        @Nullable BigDecimal budgetUsd,
        boolean hasUnpricedSpend,
        BigDecimal inFlightUsd
    ) {
        if (budgetUsd == null || spentUsd == null) {
            return LlmBudgetBlockReason.NONE;
        }
        if (LlmBudgetService.capReached(spentUsd.add(inFlightUsd), budgetUsd)) {
            return LlmBudgetBlockReason.EXHAUSTED;
        }
        return hasUnpricedSpend ? LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED : LlmBudgetBlockReason.NONE;
    }
}
