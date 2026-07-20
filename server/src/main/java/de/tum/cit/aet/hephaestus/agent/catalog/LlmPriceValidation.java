package de.tum.cit.aet.hephaestus.agent.catalog;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * Shared PRICED/FREE/UNPRICED validation (#1368) for both the instance catalog ({@link LlmModelService})
 * and workspace BYO models ({@code WorkspaceLlmModelService}) — same rule, two owners of the rates.
 *
 * <ul>
 *   <li>{@code PRICED} requires at least an input and an output rate (per 1M tokens), and every given
 *       rate must be zero or greater.</li>
 *   <li>{@code FREE}/{@code UNPRICED} must carry no rates at all; {@code FREE} additionally requires a
 *       note explaining why (e.g. self-hosted, no cost).</li>
 * </ul>
 */
final class LlmPriceValidation {

    private LlmPriceValidation() {}

    static void validate(
        PricingMode pricingMode,
        @Nullable BigDecimal per1mInputUsd,
        @Nullable BigDecimal per1mOutputUsd,
        @Nullable BigDecimal per1mCacheReadUsd,
        @Nullable BigDecimal per1mCacheWriteUsd,
        @Nullable BigDecimal per1mReasoningUsd,
        @Nullable String note
    ) {
        List<BigDecimal> rates = Arrays.asList(
            per1mInputUsd,
            per1mOutputUsd,
            per1mCacheReadUsd,
            per1mCacheWriteUsd,
            per1mReasoningUsd
        );
        boolean anyRatePresent = rates.stream().anyMatch(rate -> rate != null);

        if (pricingMode == PricingMode.PRICED) {
            if (per1mInputUsd == null || per1mOutputUsd == null) {
                throw new IllegalArgumentException(
                    "A price requires at least an input rate and an output rate (per 1M tokens)."
                );
            }
            boolean anyNegative = rates.stream().anyMatch(rate -> rate != null && rate.signum() < 0);
            if (anyNegative) {
                throw new IllegalArgumentException("Rates must be zero or greater.");
            }
        } else {
            if (anyRatePresent) {
                throw new IllegalArgumentException(
                    "Rates can only be set when the model has a price; clear them or set a price first."
                );
            }
            if (pricingMode == PricingMode.FREE && !StringUtils.hasText(note)) {
                throw new IllegalArgumentException(
                    "A note explaining why this model is free (e.g. self-hosted, no cost) is required."
                );
            }
        }
    }
}
