package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Reprice a model (#1368). Temporal supersede-on-insert: the service closes the current open
 * {@code llm_model_price} row and inserts this one as the new open row, in one transaction.
 *
 * <p>When the model has a price, {@code per1mInputUsd} and {@code per1mOutputUsd} are required (and any
 * rate given must be zero or more); otherwise every rate must be left unset. A free model requires a
 * {@code note} explaining why (e.g. self-hosted, no cost).
 */
@Schema(description = "Reprice a model; supersedes the current price")
public record UpdateLlmModelPriceRequest(
    @NonNull
    @NotNull
    @Schema(
        description = "PRICED shows the price itself; FREE is a deliberate no-cost declaration; UNPRICED shows \"No price set\""
    )
    PricingMode pricingMode,
    @Nullable
    @Schema(description = "Input rate per 1M tokens (USD); required when the model has a price")
    BigDecimal per1mInputUsd,
    @Nullable
    @Schema(description = "Output rate per 1M tokens (USD); required when the model has a price")
    BigDecimal per1mOutputUsd,
    @Nullable @Schema(description = "Cache-read rate per 1M tokens (USD), if applicable") BigDecimal per1mCacheReadUsd,
    @Nullable
    @Schema(description = "Cache-write rate per 1M tokens (USD), if applicable")
    BigDecimal per1mCacheWriteUsd,
    @Nullable
    @Schema(description = "Reasoning-token rate per 1M tokens (USD), if applicable")
    BigDecimal per1mReasoningUsd,
    @Nullable
    @Size(max = 500)
    @Schema(description = "Note; required when the model is free (e.g. self-hosted, no cost)")
    String note
) {}
