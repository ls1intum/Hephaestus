package de.tum.cit.aet.hephaestus.agent.proxy;

import static org.assertj.core.api.Assertions.assertThat;

import de.tum.cit.aet.hephaestus.agent.usage.FundingSource;
import de.tum.cit.aet.hephaestus.agent.usage.LlmPriceSnapshot;
import de.tum.cit.aet.hephaestus.agent.usage.PricingState;
import de.tum.cit.aet.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MentorTurnMeterTest extends BaseUnitTest {

    @Test
    void cacheWritesUseTheirOwnRate() {
        var price = new LlmPriceSnapshot(
                FundingSource.INSTANCE,
                PricingState.PRICED,
                1L,
                null,
                BigDecimal.ONE,
                BigDecimal.ZERO,
                BigDecimal.ZERO,
                BigDecimal.TEN);
        var meter = new MentorTurnMeter(UUID.randomUUID(), price);

        meter.add(new ProxyTokenUsage(0, 0, 0, 0, 100_000));

        assertThat(meter.spentUsd()).isEqualByComparingTo("1.00");
    }
}
