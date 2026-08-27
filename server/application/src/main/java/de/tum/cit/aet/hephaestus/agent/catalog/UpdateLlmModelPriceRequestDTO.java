package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Reprice a model; the new price supersedes the current one.
 *
 * <p>{@code @Digits} is deliberately narrower than the {@code NUMERIC(18,8)} column: every money field
 * this API returns lands in the browser as a JS {@code number}, so a rate wider than a binary64 can
 * carry would be quoted back to the admin as a different number than the one stored.
 */
@Schema(description = "Reprice a model; supersedes the current price")
public record UpdateLlmModelPriceRequestDTO(
        @NonNull
        @NotNull
        @Schema(
                description =
                        "PRICED shows the price itself; NO_CHARGE is a deliberate no-cost declaration; UNPRICED shows \"No price set\"")
        PricingMode pricingMode,

        @Nullable
        @Digits(integer = 7, fraction = 8)
        @Schema(description = "Input rate per 1M tokens (USD); required when the model has a price")
        BigDecimal per1mInputUsd,

        @Nullable
        @Digits(integer = 7, fraction = 8)
        @Schema(description = "Output rate per 1M tokens (USD); required when the model has a price")
        BigDecimal per1mOutputUsd,

        @Nullable
        @Digits(integer = 7, fraction = 8)
        @Schema(description = "Cache-read rate per 1M tokens (USD), if applicable")
        BigDecimal per1mCacheReadUsd,

        @Nullable
        @Digits(integer = 7, fraction = 8)
        @Schema(description = "Cache-write rate per 1M tokens (USD), if applicable")
        BigDecimal per1mCacheWriteUsd,

        @Nullable
        @Size(max = 500)
        @Schema(description = "Note; required when the model is free (e.g. self-hosted, no cost)")
        String note) {}
