package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Create a model on a workspace's own AI provider connection (#1368). Pricing is inline (no separate
 * reprice endpoint — a BYO model has no price history): {@code pricingMode} defaults to
 * {@code UNPRICED} when omitted and is validated by {@link LlmPriceValidation}.
 */
@Schema(description = "Create a model on your AI provider")
public record CreateWorkspaceLlmModelRequestDTO(
    @Nullable
    @Size(max = 63)
    @Schema(description = "Optional internal slug; generated from displayName when omitted")
    String slug,
    @NonNull @NotBlank @Size(max = 128) @Schema(description = "Human-readable name") String displayName,
    @NonNull @NotBlank @Size(max = 256) @Schema(description = "Upstream provider model id") String upstreamModelId,
    @Nullable @PositiveOrZero @Schema(description = "Context window in tokens") Integer contextWindow,
    @Nullable @PositiveOrZero @Schema(description = "Maximum output tokens") Integer maxOutputTokens,
    @Nullable @Schema(description = "Whether the model supports a reasoning mode") Boolean supportsReasoning,
    @Nullable @Schema(description = "Whether the model is active (default false)") Boolean enabled,
    @Nullable @Schema(description = "Pricing mode (default UNPRICED)") PricingMode pricingMode,
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
    @Size(max = 500)
    @Schema(description = "Note; required when the model is free (e.g. self-hosted, no cost)")
    String priceNote
) {}
