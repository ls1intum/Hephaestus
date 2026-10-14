package de.tum.cit.aet.hephaestus.agent.scan;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import de.tum.cit.aet.hephaestus.core.llm.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsageFundingSource;
import de.tum.cit.aet.hephaestus.core.llm.usage.UsagePricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class ScanBudgetTest extends BaseUnitTest {
    @Test
    void reservesTheHighestCacheRateAsAnInputBound() {
        var price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE,
                UsagePricingState.PRICED,
                1L,
                null,
                new BigDecimal("2"),
                new BigDecimal("4"),
                new BigDecimal("1"),
                new BigDecimal("8"));
        assertThat(ScanBudget.cost(price, 1000000, 500000)).isEqualByComparingTo("10");
        assertThat(ScanBudget.rate(price)).isEqualByComparingTo("8");
    }

    @Test
    void missingPricedBucketsFollowTheLedgerContract() {
        var price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE, UsagePricingState.PRICED, 1L, null, null, BigDecimal.ONE, null, null);
        assertThat(ScanBudget.cost(price, 1000000, 1000000)).isEqualByComparingTo("1");
    }

    @Test
    void anUnknownPriceCannotBeReportedAsFree() {
        assertThatThrownBy(() -> ScanBudget.cost(LlmPriceSnapshot.unpricedInstance(), 1, 1))
                .isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> ScanBudget.rate(LlmPriceSnapshot.unpricedInstance()))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void explicitlyFreeModelsHaveZeroDollarReservation() {
        var price = new LlmPriceSnapshot(
                UsageFundingSource.INSTANCE, UsagePricingState.NO_CHARGE, null, null, null, null, null, null);
        assertThat(ScanBudget.cost(price, 1000000, 1000000)).isZero();
    }
}
