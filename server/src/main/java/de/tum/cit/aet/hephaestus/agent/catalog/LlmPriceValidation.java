package de.tum.cit.aet.hephaestus.agent.catalog;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import org.jspecify.annotations.Nullable;
import org.springframework.util.StringUtils;

/**
 * Shared PRICED/NO_CHARGE/UNPRICED validation for both the instance catalog ({@link LlmModelService})
 * and workspace BYO models ({@code WorkspaceLlmModelService}) — same rule, two owners of the rates.
 *
 * <ul>
 *   <li>{@code PRICED} requires at least an input and an output rate (per 1M tokens), every given
 *       rate must be zero or greater, and at least one rate must be strictly greater than zero — an
 *       all-zero PRICED model would otherwise pass validation and count as verified $0 spend
 *       forever, which is what {@code Free} is for.</li>
 *   <li>{@code NO_CHARGE}/{@code UNPRICED} must carry no rates at all; {@code NO_CHARGE} additionally requires a
 *       note explaining why (e.g. self-hosted, no cost).</li>
 *   <li>Every rate must be below {@link #MAX_RATE_EXCLUSIVE}.</li>
 * </ul>
 *
 * <p>The magnitude bound lives HERE, and not only in the request DTOs' {@code @Digits}, because the
 * rates have four entry points (instance create/reprice, workspace BYO create/update) and only one of
 * them may not silently widen it. A rate that clears {@code NUMERIC(18,8)} but not binary64 is quoted
 * back to the admin as a different number than the one stored, which is the failure this prevents —
 * see {@code MoneyWirePrecisionTest} for where the bound comes from.
 */
final class LlmPriceValidation {

    /**
     * Rates are {@code NUMERIC(18,8)}. At scale 8 a binary64 round-trips exactly only below
     * {@code 10^7} (15 significant digits), so this — not the column — is the real ceiling.
     */
    static final BigDecimal MAX_RATE_EXCLUSIVE = new BigDecimal("10000000");

    private LlmPriceValidation() {}

    static void validate(
        PricingMode pricingMode,
        @Nullable BigDecimal per1mInputUsd,
        @Nullable BigDecimal per1mOutputUsd,
        @Nullable BigDecimal per1mCacheReadUsd,
        @Nullable BigDecimal per1mCacheWriteUsd,
        @Nullable String note
    ) {
        List<BigDecimal> rates = Arrays.asList(per1mInputUsd, per1mOutputUsd, per1mCacheReadUsd, per1mCacheWriteUsd);
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
            boolean anyPositive = rates.stream().anyMatch(rate -> rate != null && rate.signum() > 0);
            if (!anyPositive) {
                throw new IllegalArgumentException(
                    "A price requires at least one rate greater than zero. For a free model, choose Free instead."
                );
            }
            boolean anyTooLarge = rates
                .stream()
                .anyMatch(rate -> rate != null && rate.compareTo(MAX_RATE_EXCLUSIVE) >= 0);
            if (anyTooLarge) {
                throw new IllegalArgumentException(
                    "Rates must be below " + MAX_RATE_EXCLUSIVE.toPlainString() + " per 1M tokens."
                );
            }
        } else {
            if (anyRatePresent) {
                throw new IllegalArgumentException(
                    "Rates can only be set when the model has a price; clear them or set a price first."
                );
            }
            if (pricingMode == PricingMode.NO_CHARGE && !StringUtils.hasText(note)) {
                throw new IllegalArgumentException(
                    "A note explaining why this model is free (e.g. self-hosted, no cost) is required."
                );
            }
        }
    }
}
