package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * A workspace's two caps and what the ledger says has been spent against each, kept as numbers so a
 * caller that can see spend the ledger cannot yet — the LLM proxy, mid-attempt — can add it before the
 * comparison rather than after the money is gone.
 *
 * <p>A {@code null} budget is an uncapped purse: never blocked, and its spend is never even queried,
 * so the matching {@code spent} field is {@code null} too.
 *
 * <p>{@code hasUnpricedSpend} may read {@code false} on a purse that is already exhausted on ledger
 * spend alone, because the producer skips the probe there. Harmless: in-flight spend is never negative,
 * so such a purse stays exhausted, and EXHAUSTED outranks UNVERIFIABLE.
 */
public record LlmBudgetHeadroom(
        @Nullable BigDecimal instanceSpentUsd,
        @Nullable BigDecimal instanceBudgetUsd,
        boolean instanceHasUnpricedSpend,
        @Nullable BigDecimal workspaceSpentUsd,
        @Nullable BigDecimal workspaceBudgetUsd,
        boolean workspaceHasUnpricedSpend) {
    public static final LlmBudgetHeadroom UNCAPPED = new LlmBudgetHeadroom(null, null, false, null, null, false);

    /** The verdict on recorded spend alone. */
    public LlmBudgetDecision decide() {
        return decideWith(null, BigDecimal.ZERO);
    }

    /**
     * The verdict once {@code inFlightUsd} of not-yet-recorded spend is charged to {@code purse}.
     *
     * @param purse who pays; {@code null} means unattributable, and the amount is then charged to both
     *     purses rather than to neither. Fail-safe, not fail-open.
     * @param inFlightUsd already-incurred spend the ledger cannot see yet; never negative
     */
    public LlmBudgetDecision decideWith(@Nullable FundingSource purse, BigDecimal inFlightUsd) {
        return new LlmBudgetDecision(
                reason(
                        instanceSpentUsd,
                        instanceBudgetUsd,
                        instanceHasUnpricedSpend,
                        chargedTo(FundingSource.INSTANCE, purse, inFlightUsd)),
                reason(
                        workspaceSpentUsd,
                        workspaceBudgetUsd,
                        workspaceHasUnpricedSpend,
                        chargedTo(FundingSource.WORKSPACE, purse, inFlightUsd)));
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
            BigDecimal inFlightUsd) {
        if (budgetUsd == null || spentUsd == null) {
            return LlmBudgetBlockReason.NONE;
        }
        if (LlmBudgetService.capReached(spentUsd.add(inFlightUsd), budgetUsd)) {
            return LlmBudgetBlockReason.EXHAUSTED;
        }
        return hasUnpricedSpend ? LlmBudgetBlockReason.UNPRICED_USAGE_BLOCKED : LlmBudgetBlockReason.NONE;
    }
}
