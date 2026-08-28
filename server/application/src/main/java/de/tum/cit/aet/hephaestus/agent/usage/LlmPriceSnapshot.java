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

    /** Decimal places the ledger keeps: {@code llm_usage_event.cost_usd} is {@code NUMERIC(18,6)}. */
    static final int LEDGER_SCALE = 6;

    /** Banker's rounding, so a long run of amounts does not drift up. */
    static final RoundingMode LEDGER_ROUNDING = RoundingMode.HALF_EVEN;

    /** Wide enough that the only rounding deciding a stored amount is the {@link #LEDGER_SCALE} one. */
    private static final MathContext CALC = new MathContext(20, LEDGER_ROUNDING);

    /** The smallest amount the ledger column can hold. */
    private static final BigDecimal MIN_COST = BigDecimal.ONE.movePointLeft(LEDGER_SCALE);

    /**
     * Not the column's maximum but the wire's, which is narrower: a cost leaves as a JSON number and is
     * read into a binary64, which reproduces at most fifteen significant digits exactly — at scale 6,
     * everything below this. Storing more would mean the API cannot state the amount without changing it.
     */
    private static final BigDecimal EXACT_ON_WIRE_CEILING = BigDecimal.valueOf(1_000_000_000L);

    private static final BigDecimal MAX_COST = EXACT_ON_WIRE_CEILING.subtract(MIN_COST);

    /**
     * The price of an attempt whose real price cannot be recovered. Instance-funded because the host's
     * shared models are what an unattributed run consumed; UNPRICED so the month reads unverifiable
     * rather than free.
     */
    public static LlmPriceSnapshot unpricedInstance() {
        return new LlmPriceSnapshot(FundingSource.INSTANCE, PricingState.UNPRICED, null, null, null, null, null, null);
    }

    /** Which way a stored cost was moved to fit the ledger column, when it was not stored as computed. */
    public enum CostClamp {
        /**
         * A positive cost below one micro-dollar, rounded up rather than to zero, so "we made a paid
         * call" stays distinguishable from "this was free". Over-bills by less than $0.000001.
         */
        ROUNDED_UP_TO_MINIMUM,
        /** Under-bills, so every budget computed from it reads low. Means a price or token count is wrong. */
        CAPPED_AT_MAXIMUM,
    }

    /**
     * @param usd the amount to store; {@code null} only when no price was resolved at all
     * @param clamp {@code null} when {@code usd} is exactly what the frozen rates produced
     */
    public record Cost(@Nullable BigDecimal usd, @Nullable CostClamp clamp) {
        static Cost exact(@Nullable BigDecimal usd) {
            return new Cost(usd, null);
        }
    }

    /** Takes no reasoning tokens: they are already inside {@code outputTokens} and must not be priced twice. */
    public Cost calculateCost(long inputTokens, long outputTokens, long cacheReadTokens, long cacheWriteTokens) {
        if (pricingState == PricingState.UNPRICED) return Cost.exact(null);
        if (pricingState == PricingState.NO_CHARGE) return Cost.exact(BigDecimal.ZERO.setScale(LEDGER_SCALE));
        BigDecimal raw = bucket(inputTokens, per1mInputUsd, CALC)
            .add(bucket(outputTokens, per1mOutputUsd, CALC), CALC)
            .add(bucket(cacheReadTokens, per1mCacheReadUsd, CALC), CALC)
            .add(bucket(cacheWriteTokens, per1mCacheWriteUsd, CALC), CALC);
        if (raw.signum() < 0) throw new IllegalStateException("Frozen LLM price produced a negative cost");
        BigDecimal rounded = raw.setScale(LEDGER_SCALE, LEDGER_ROUNDING);
        if (raw.signum() > 0 && rounded.signum() == 0) {
            return new Cost(MIN_COST, CostClamp.ROUNDED_UP_TO_MINIMUM);
        }
        if (rounded.compareTo(EXACT_ON_WIRE_CEILING) >= 0) {
            return new Cost(MAX_COST, CostClamp.CAPPED_AT_MAXIMUM);
        }
        return Cost.exact(rounded);
    }

    private static BigDecimal bucket(long tokens, @Nullable BigDecimal rate, MathContext mc) {
        if (tokens <= 0 || rate == null) return BigDecimal.ZERO;
        return BigDecimal.valueOf(tokens).divide(PER_1M, mc).multiply(rate, mc);
    }
}
