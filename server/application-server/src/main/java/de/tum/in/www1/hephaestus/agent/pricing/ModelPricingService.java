package de.tum.in.www1.hephaestus.agent.pricing;

import java.math.BigDecimal;
import java.math.MathContext;
import java.math.RoundingMode;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Computes per-turn LLM cost in USD from Pi {@code agent_end} token usage. Decimal arithmetic
 * (BigDecimal, 6-decimal-place output) because rounding errors on tens-of-thousands of turns
 * would accumulate to user-visible amounts.
 *
 * <p>Unknown models return {@link Optional#empty()}; callers MUST persist a NULL on the
 * assistant message rather than coercing to zero — distinguishing "free model" from
 * "missing-from-table" is operationally important. The {@link ModelPricingRepository} is
 * idempotently cached by JPA's L2 cache, but no Spring-level {@code @Cacheable} layer: a
 * pricing rollover that misses cache invalidation would silently overcharge for the TTL
 * window, and we'd rather take one DB hit per turn than risk that. Hot-path is well within
 * the existing per-turn budget.
 */
@Service
@RequiredArgsConstructor
public class ModelPricingService {

    /**
     * Six significant decimal digits — matches the storage column scale and gives sub-cent
     * resolution down to thousand-token granularity.
     */
    public static final int COST_SCALE = 6;

    private static final BigDecimal PER_1K_DIVISOR = BigDecimal.valueOf(1000L);

    private final ModelPricingRepository pricingRepository;

    /**
     * Compute the cost of one turn. Returns {@link Optional#empty()} when the model id is not
     * registered in {@code model_pricing} — caller persists NULL in that case.
     *
     * @param modelId         vendor model identifier as reported in Pi's {@code agent_end.model} field
     * @param inputTokens     non-cache prompt tokens
     * @param outputTokens    completion tokens
     * @param cacheReadTokens cached prompt-token reads (cheaper than fresh input)
     * @param cacheWriteTokens cached prompt-token writes (typically more expensive than reads)
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> computeCost(
        String modelId,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
    ) {
        if (modelId == null || modelId.isBlank()) {
            return Optional.empty();
        }
        return pricingRepository
            .findByModelId(modelId)
            .map(pricing -> sum(pricing, inputTokens, outputTokens, cacheReadTokens, cacheWriteTokens));
    }

    private static BigDecimal sum(
        ModelPricing pricing,
        long inputTokens,
        long outputTokens,
        long cacheReadTokens,
        long cacheWriteTokens
    ) {
        // (tokens / 1000) * rate, summed across four buckets. We use HALF_EVEN to avoid the
        // long-run bias of HALF_UP (sub-cent accumulation matters at scale).
        MathContext mc = new MathContext(16, RoundingMode.HALF_EVEN);
        BigDecimal total = perBucket(inputTokens, pricing.getPer1kInputUsd(), mc)
            .add(perBucket(outputTokens, pricing.getPer1kOutputUsd(), mc), mc)
            .add(perBucket(cacheReadTokens, pricing.getPer1kCacheReadUsd(), mc), mc)
            .add(perBucket(cacheWriteTokens, pricing.getPer1kCacheWriteUsd(), mc), mc);
        return total.setScale(COST_SCALE, RoundingMode.HALF_EVEN);
    }

    private static BigDecimal perBucket(long tokens, BigDecimal rate, MathContext mc) {
        if (rate == null || tokens <= 0) {
            return BigDecimal.ZERO;
        }
        return BigDecimal.valueOf(tokens).divide(PER_1K_DIVISOR, mc).multiply(rate, mc);
    }
}
