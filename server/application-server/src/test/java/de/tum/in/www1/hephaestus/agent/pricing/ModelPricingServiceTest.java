package de.tum.in.www1.hephaestus.agent.pricing;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import de.tum.in.www1.hephaestus.testconfig.BaseUnitTest;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

@DisplayName("ModelPricingService")
class ModelPricingServiceTest extends BaseUnitTest {

    @Mock
    private ModelPricingRepository repository;

    private ModelPricingService service;

    @BeforeEach
    void setUp() {
        service = new ModelPricingService(repository);
    }

    @Test
    @DisplayName("Unknown model → Optional.empty()")
    void unknownModelReturnsEmpty() {
        when(repository.findActive(eq("nope"), any(Instant.class))).thenReturn(Optional.empty());
        assertThat(service.computeCost("nope", 100, 100, 0, 0)).isEmpty();
    }

    @Test
    @DisplayName("Null / blank model → Optional.empty()")
    void blankModelReturnsEmpty() {
        assertThat(service.computeCost(null, 0, 0, 0, 0)).isEmpty();
        assertThat(service.computeCost("", 0, 0, 0, 0)).isEmpty();
        assertThat(service.computeCost("  ", 0, 0, 0, 0)).isEmpty();
    }

    @Test
    @DisplayName("Known model with input + output tokens computes (tokens/1k) × rate")
    void knownModelComputesCost() {
        when(repository.findActive(eq("gpt-4o-mini"), any(Instant.class))).thenReturn(Optional.of(fixture()));
        // 1500 input → 1.5 × 0.000150 = 0.000225
        // 500 output → 0.5 × 0.000600 = 0.000300
        // total = 0.000525
        Optional<BigDecimal> cost = service.computeCost("gpt-4o-mini", 1500, 500, 0, 0);
        assertThat(cost).isPresent();
        assertThat(cost.get()).isEqualByComparingTo(new BigDecimal("0.000525"));
    }

    @Test
    @DisplayName("Negative token counts treated as zero (no negative cost)")
    void negativeTokensSkipBucket() {
        when(repository.findActive(eq("gpt-4o-mini"), any(Instant.class))).thenReturn(Optional.of(fixture()));
        Optional<BigDecimal> cost = service.computeCost("gpt-4o-mini", -10, 0, 0, 0);
        assertThat(cost).isPresent();
        assertThat(cost.get()).isEqualByComparingTo(BigDecimal.ZERO.setScale(ModelPricingService.COST_SCALE));
    }

    @Test
    @DisplayName("Cache tokens contribute to the total")
    void cacheTokensAreSummed() {
        when(repository.findActive(eq("claude-3-5-sonnet"), any(Instant.class))).thenReturn(
            Optional.of(claudeFixture())
        );
        // 1000 input → 1 × 0.003 = 0.003
        // 1000 output → 1 × 0.015 = 0.015
        // 1000 cache_read → 1 × 0.0003 = 0.0003
        // 1000 cache_write → 1 × 0.00375 = 0.00375
        // total = 0.02205
        Optional<BigDecimal> cost = service.computeCost("claude-3-5-sonnet", 1000, 1000, 1000, 1000);
        assertThat(cost).isPresent();
        assertThat(cost.get()).isEqualByComparingTo(new BigDecimal("0.022050"));
    }

    private ModelPricing fixture() {
        ModelPricing m = new ModelPricing();
        m.setModelId("gpt-4o-mini");
        m.setPer1kInputUsd(new BigDecimal("0.000150"));
        m.setPer1kOutputUsd(new BigDecimal("0.000600"));
        m.setPer1kCacheReadUsd(new BigDecimal("0.000075"));
        m.setPer1kCacheWriteUsd(BigDecimal.ZERO);
        return m;
    }

    private ModelPricing claudeFixture() {
        ModelPricing m = new ModelPricing();
        m.setModelId("claude-3-5-sonnet");
        m.setPer1kInputUsd(new BigDecimal("0.003000"));
        m.setPer1kOutputUsd(new BigDecimal("0.015000"));
        m.setPer1kCacheReadUsd(new BigDecimal("0.000300"));
        m.setPer1kCacheWriteUsd(new BigDecimal("0.003750"));
        return m;
    }
}
