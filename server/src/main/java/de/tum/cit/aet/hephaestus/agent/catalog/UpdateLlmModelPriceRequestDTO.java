package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Reprice a model. Temporal supersede-on-insert: the service closes the current open
 * {@code llm_model_price} row and inserts this one as the new open row, in one transaction.
 *
 * <p>When the model has a price, {@code per1mInputUsd} and {@code per1mOutputUsd} are required (and any
 * rate given must be zero or more); otherwise every rate must be left unset. A free model requires a
 * {@code note} explaining why (e.g. self-hosted, no cost).
 *
 * <p><b>Why the rates are narrower than their column.</b> {@code llm_model_price} is
 * {@code NUMERIC(18,8)}, which accepts eighteen significant digits — three more than a binary64 can
 * carry back. Every money field this API returns lands in the browser as a JS {@code number}, so a rate
 * wider than fifteen significant digits would be quoted back to the admin as a different number than
 * the one stored ({@code 9999999999.99999999} renders as {@code 10000000000}). {@code @Digits} pins the
 * accepted width to the bound {@code MoneyWirePrecisionTest} measures — 7 integer digits at scale 8,
 * i.e. rates below $10,000,000 per million tokens — so the column's extra width is unreachable rather
 * than merely unused. The same instrument {@code UpdateLlmBudgetRequestDTO} uses on caps.
 */
@Schema(description = "Reprice a model; supersedes the current price")
public record UpdateLlmModelPriceRequestDTO(
    @NonNull
    @NotNull
    @Schema(
        description = "PRICED shows the price itself; NO_CHARGE is a deliberate no-cost declaration; UNPRICED shows \"No price set\""
    )
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
    String note
) {}
