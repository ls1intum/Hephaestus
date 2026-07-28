package de.tum.cit.aet.hephaestus.agent.usage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class LlmPriceSnapshotTest extends BaseUnitTest {

    private static LlmPriceSnapshot priced(@Nullable String inputRate) {
        return new LlmPriceSnapshot(
            FundingSource.INSTANCE,
            PricingState.PRICED,
            1L,
            null,
            inputRate == null ? null : new BigDecimal(inputRate),
            BigDecimal.ZERO,
            BigDecimal.ZERO,
            BigDecimal.ZERO
        );
    }

    @Nested
    @DisplayName("exact costs")
    class Exact {

        @Test
        @DisplayName("a whole million input tokens costs exactly the per-1M rate")
        void perMillionRateIsAppliedPerMillionTokens() {
            LlmPriceSnapshot.Cost cost = priced("3.50").calculateCost(1_000_000, 0, 0, 0);

            assertThat(cost.usd()).isEqualByComparingTo("3.50");
            assertThat(cost.clamp()).isNull();
        }

        @Test
        @DisplayName("an unpriced model yields no cost at all, not a zero")
        void anUnpricedModelYieldsNoCostAtAllRatherThanAZero() {
            LlmPriceSnapshot unpriced = new LlmPriceSnapshot(
                FundingSource.INSTANCE,
                PricingState.UNPRICED,
                null,
                null,
                null,
                null,
                null,
                null
            );

            assertThat(unpriced.calculateCost(1_000_000, 1_000_000, 0, 0).usd()).isNull();
        }

        /**
         * NO_CHARGE is a declaration by the operator, not an absence of rates: admission passes a
         * declared-free model's real rates through untouched. The rates below are therefore the case
         * that matters — with the short-circuit removed they produce $7.50 and bill a workspace for a
         * model its administrator declared free. An all-null-rate fixture would reach zero either way
         * and assert nothing.
         */
        @Test
        @DisplayName("a declared-free model is exactly zero even when it carries real rates, and that is not a clamp")
        void noChargeIsZeroDespiteCarryingRates() {
            LlmPriceSnapshot free = new LlmPriceSnapshot(
                FundingSource.WORKSPACE,
                PricingState.NO_CHARGE,
                null,
                9L,
                new BigDecimal("1"),
                new BigDecimal("2"),
                new BigDecimal("3"),
                new BigDecimal("4")
            );

            LlmPriceSnapshot.Cost cost = free.calculateCost(1_000_000, 2_000_000, 500_000, 250_000);

            assertThat(cost.usd()).isEqualByComparingTo("0");
            assertThat(cost.clamp()).isNull();
        }

        @Test
        void aNegativeRateIsRefusedRatherThanCredited() {
            assertThatThrownBy(() -> priced("-1.00").calculateCost(1_000_000, 0, 0, 0))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("negative");
        }
    }

    /**
     * The two amounts a computed cost is moved to when it will not fit, each paired with the largest
     * value that is NOT moved. The exact-boundary pairs matter more than the extremes: an off-by-one in
     * either comparison would move real money and leave no trace, since a clamped amount looks like an
     * ordinary cost once it is in the ledger.
     *
     * <p>The upper bound is the WIRE's, not the column's. {@code NUMERIC(18,6)} holds twelve integer
     * digits, but a cost is served as a JSON number and read into a binary64, which reproduces fifteen
     * significant digits — at scale 6, everything below $1,000,000,000
     * ({@link MoneyWirePrecisionTest}). Clamping at the column's maximum instead would let the ledger
     * hold amounts the API cannot state without changing them.
     */
    @Nested
    @DisplayName("clamped amounts")
    class Clamps {

        @Test
        @DisplayName("a positive cost that rounds to zero is raised to one micro-dollar, and flagged")
        void subMicroDollarCostIsRoundedUpNotAwayToZero() {
            // 1 token at $0.0001 per 1M = $0.0000000001 — six decimals cannot hold it.
            LlmPriceSnapshot.Cost cost = priced("0.0001").calculateCost(1, 0, 0, 0);

            assertThat(cost.usd()).isEqualByComparingTo("0.000001");
            assertThat(cost.clamp()).isEqualTo(LlmPriceSnapshot.CostClamp.ROUNDED_UP_TO_MINIMUM);
        }

        @Test
        @DisplayName("the smallest cost that fits is stored as computed, with no flag")
        void exactlyOneMicroDollarIsNotAClamp() {
            // 1 token at $1 per 1M = exactly $0.000001.
            LlmPriceSnapshot.Cost cost = priced("1").calculateCost(1, 0, 0, 0);

            assertThat(cost.usd()).isEqualByComparingTo("0.000001");
            assertThat(cost.clamp()).isNull();
        }

        @Test
        @DisplayName("zero tokens on a priced model is a true zero, never raised to the minimum")
        void zeroTokensStaysZero() {
            LlmPriceSnapshot.Cost cost = priced("1000").calculateCost(0, 0, 0, 0);

            assertThat(cost.usd()).isEqualByComparingTo("0");
            assertThat(cost.clamp()).isNull();
        }

        @Test
        @DisplayName("a cost that would not survive the wire is capped at the largest one that does")
        void costAtOrBeyondOneBillionDollarsIsCappedAndFlagged() {
            // 1M tokens at $1e9 per 1M = exactly $1,000,000,000 — the first amount a browser can no
            // longer reproduce digit for digit at scale 6.
            LlmPriceSnapshot.Cost cost = priced("1000000000").calculateCost(1_000_000, 0, 0, 0);

            assertThat(cost.usd()).isEqualByComparingTo("999999999.999999");
            assertThat(cost.clamp()).isEqualTo(LlmPriceSnapshot.CostClamp.CAPPED_AT_MAXIMUM);
        }

        @Test
        @DisplayName("the largest cost below the ceiling is stored exactly as computed")
        void justBelowTheCeilingIsNotAClamp() {
            LlmPriceSnapshot.Cost cost = priced("999999999.999999").calculateCost(1_000_000, 0, 0, 0);

            assertThat(cost.usd()).isEqualByComparingTo("999999999.999999");
            assertThat(cost.clamp()).isNull();
        }
    }
}
