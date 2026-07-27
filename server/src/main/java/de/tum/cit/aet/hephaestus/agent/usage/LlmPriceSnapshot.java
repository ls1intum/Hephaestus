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

    /** How every amount reaches {@link #LEDGER_SCALE}. Banker's rounding, so a long run does not drift up. */
    static final RoundingMode LEDGER_ROUNDING = RoundingMode.HALF_EVEN;

    /**
     * Working precision for the intermediate arithmetic — wide enough that the only rounding that
     * decides a stored amount is the single {@link #LEDGER_SCALE} step at the end.
     */
    private static final MathContext CALC = new MathContext(20, LEDGER_ROUNDING);

    /** The smallest amount the ledger column can hold: one unit at {@link #LEDGER_SCALE}. */
    private static final BigDecimal MIN_COST = BigDecimal.ONE.movePointLeft(LEDGER_SCALE);

    /**
     * The largest amount that survives the whole trip, which is NOT the column's own maximum.
     *
     * <p>{@code NUMERIC(18,6)} stops at {@code 999999999999.999999} (twelve integer digits), but a cost
     * leaves this server as a JSON number and is read into a binary64 by the browser, which reproduces
     * at most fifteen significant digits exactly. At scale 6 that is everything below
     * {@code 1_000_000_000} — a thousandfold inside the column. Clamping to the column maximum instead
     * would store amounts the API cannot state without changing them, so the clamp is set to the bound
     * that actually holds end to end. {@code MoneyWirePrecisionTest} pins that bound and the cliff just
     * past it; {@code LlmPriceSnapshotTest} pins that this constant sits on it.
     *
     * <p>Nothing legitimate reaches either bound: a billion dollars of tokens in one unit of work means
     * a price or a token count is wrong, which is why crossing it is WARNed and counted rather than
     * quietly stored.
     */
    private static final BigDecimal EXACT_ON_WIRE_CEILING = BigDecimal.valueOf(1_000_000_000L);

    /** The largest storable amount below the ceiling: one ledger unit under it. */
    private static final BigDecimal MAX_COST = EXACT_ON_WIRE_CEILING.subtract(MIN_COST);

    /**
     * The price of an attempt whose real price cannot be recovered — a job frozen before admission
     * pricing existed, or a terminal path that must never throw. Instance-funded because the host's
     * shared models are what an unattributed run consumed; UNPRICED so the month reads unverifiable
     * rather than free.
     */
    public static LlmPriceSnapshot unpricedInstance() {
        return new LlmPriceSnapshot(FundingSource.INSTANCE, PricingState.UNPRICED, null, null, null, null, null, null);
    }

    /**
     * Whether the stored cost is the computed one, and if not, which way it was moved. Money is never
     * altered silently: {@link LlmUsageRecorder} logs and counts every non-{@code null} value here
     * under {@code llm.usage.cost.clamped}, so an operator can find the events whose ledger amount is
     * not what the frozen rates produced.
     */
    public enum CostClamp {
        /**
         * A positive cost smaller than one micro-dollar, rounded up to {@link #MIN_COST} rather than
         * to zero. Over-bills by less than $0.000001, and keeps "we made a paid call" distinguishable
         * from "this was free" in the ledger.
         */
        ROUNDED_UP_TO_MINIMUM,
        /**
         * A cost at or beyond {@link #EXACT_ON_WIRE_CEILING}, capped at {@link #MAX_COST}. Under-bills,
         * and a budget computed from it therefore understates real spend. Reaching this means a price
         * or a token count is almost certainly wrong — a billion dollars of tokens in one unit of work
         * is not a thing that happens.
         */
        CAPPED_AT_MAXIMUM,
    }

    /**
     * A computed cost together with any adjustment made to fit it into the ledger column.
     *
     * @param usd the amount to store; {@code null} only when no price was resolved at all
     * @param clamp {@code null} when {@code usd} is exactly what the frozen rates produced
     */
    public record Cost(@Nullable BigDecimal usd, @Nullable CostClamp clamp) {
        static Cost exact(@Nullable BigDecimal usd) {
            return new Cost(usd, null);
        }
    }

    /** Computes the authoritative ledger/UI cost. Reasoning is already included in output tokens. */
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
