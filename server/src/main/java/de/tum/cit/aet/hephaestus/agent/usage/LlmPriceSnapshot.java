package de.tum.cit.aet.hephaestus.agent.usage;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import org.jspecify.annotations.Nullable;

/** Immutable pricing provenance captured when an LLM turn is admitted. */
public record LlmPriceSnapshot(
    FundingSource fundingSource,
    PricingState pricingState,
    @Nullable Long appliedPriceId,
    @Nullable Long appliedWorkspaceModelId,
    @Nullable BigDecimal per1mInputUsd,
    @Nullable BigDecimal per1mOutputUsd,
    @Nullable BigDecimal per1mCacheReadUsd,
    @Nullable BigDecimal per1mCacheWriteUsd
) {
    private static final BigDecimal PER_1M = BigDecimal.valueOf(1_000_000L);
    private static final BigDecimal MIN_COST = new BigDecimal("0.000001");
    private static final BigDecimal MAX_COST = new BigDecimal("999999999999.999999");

    /** Computes the authoritative ledger/UI cost. Reasoning is already included in output tokens. */
    public @Nullable BigDecimal calculateCost(
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
    ) {
        if (pricingState == PricingState.UNPRICED) return null;
        if (pricingState == PricingState.NO_CHARGE) return BigDecimal.ZERO.setScale(6);
        MathContext mc = new MathContext(20, RoundingMode.HALF_EVEN);
        BigDecimal raw = bucket(inputTokens, per1mInputUsd, mc)
            .add(bucket(outputTokens, per1mOutputUsd, mc), mc)
            .add(bucket(cacheReadTokens, per1mCacheReadUsd, mc), mc)
            .add(bucket(cacheWriteTokens, per1mCacheWriteUsd, mc), mc);
        if (raw.signum() < 0) throw new IllegalStateException("Frozen LLM price produced a negative cost");
        BigDecimal rounded = raw.setScale(6, RoundingMode.HALF_EVEN);
        if (raw.signum() > 0 && rounded.signum() == 0) return MIN_COST;
        return rounded.compareTo(BigDecimal.valueOf(1_000_000_000_000L)) >= 0 ? MAX_COST : rounded;
    }

    private static BigDecimal bucket(long tokens, @Nullable BigDecimal rate, MathContext mc) {
        if (tokens <= 0 || rate == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(tokens).divide(PER_1M, mc).multiply(rate, mc);
    }
}
