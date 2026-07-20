package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.Instant;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** A model's price row (#1368) — either the current open row or a superseded historical one. */
@Schema(description = "A model's price, per 1M tokens")
public record LlmModelPriceDTO(
    @NonNull @Schema(description = "Price row id") Long id,
    @NonNull @Schema(description = "Pricing mode") PricingMode pricingMode,
    @Nullable @Schema(description = "Input rate per 1M tokens (USD)") BigDecimal per1mInputUsd,
    @Nullable @Schema(description = "Output rate per 1M tokens (USD)") BigDecimal per1mOutputUsd,
    @Nullable @Schema(description = "Cache-read rate per 1M tokens (USD)") BigDecimal per1mCacheReadUsd,
    @Nullable @Schema(description = "Cache-write rate per 1M tokens (USD)") BigDecimal per1mCacheWriteUsd,
    @Nullable @Schema(description = "Reasoning-token rate per 1M tokens (USD)") BigDecimal per1mReasoningUsd,
    @NonNull @Schema(description = "Currency code") String currency,
    @Nullable @Schema(description = "Note") String note,
    @NonNull @Schema(description = "When this price took effect") Instant effectiveFrom,
    @Nullable @Schema(description = "When this price was superseded; null if still current") Instant effectiveTo
) {
    public static LlmModelPriceDTO from(LlmModelPrice price) {
        return new LlmModelPriceDTO(
            price.getId(),
            price.getPricingMode(),
            price.getPer1mInputUsd(),
            price.getPer1mOutputUsd(),
            price.getPer1mCacheReadUsd(),
            price.getPer1mCacheWriteUsd(),
            price.getPer1mReasoningUsd(),
            price.getCurrency(),
            price.getNote(),
            price.getEffectiveFrom(),
            price.getEffectiveTo()
        );
    }
}
