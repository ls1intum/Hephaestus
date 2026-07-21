package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
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
    @NonNull
    @NotBlank
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{0,62}$",
        message = "slug must be lowercase alphanumeric with hyphens (max 63 chars)"
    )
    @Schema(description = "Unique slug within the workspace", example = "gpt-5-eu")
    String slug,
    @NonNull @NotBlank @Size(max = 128) @Schema(description = "Human-readable name") String displayName,
    @NonNull @NotBlank @Size(max = 256) @Schema(description = "Upstream provider model id") String upstreamModelId,
    @Nullable
    @Size(max = 40)
    @Schema(description = "Wire protocol override for this model; defaults to the connection's protocol")
    String apiProtocolOverride,
    @Nullable @Schema(description = "What surface this model serves (default CHAT)") ModelModality modality,
    @Nullable @PositiveOrZero @Schema(description = "Context window in tokens") Integer contextWindow,
    @Nullable @PositiveOrZero @Schema(description = "Maximum output tokens") Integer maxOutputTokens,
    @Nullable @Schema(description = "Whether the model supports a reasoning mode") Boolean supportsReasoning,
    @Nullable
    @Size(max = 16)
    @Schema(description = "Cache-control wire format, if applicable")
    String cacheControlFormat,
    @Nullable @Schema(description = "Whether the model is active (default true)") Boolean enabled,
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
    @Schema(description = "Reasoning-token rate per 1M tokens (USD), if applicable")
    BigDecimal per1mReasoningUsd,
    @Nullable
    @Size(max = 500)
    @Schema(description = "Note; required when the model is free (e.g. self-hosted, no cost)")
    String priceNote
) {}
