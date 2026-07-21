package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import org.jspecify.annotations.Nullable;

/**
 * Partial update of a model on a workspace's own AI provider connection (#1368). Every field is
 * optional; an absent (null) field keeps its current value.
 *
 * <p>Pricing is all-or-nothing: the service only touches the price fields when {@code pricingMode} is
 * given, and then applies the rates/note exactly as supplied (an omitted rate is cleared, matching the
 * instance-side reprice endpoint's replace-not-merge semantics). Supplying a rate without a
 * {@code pricingMode} is a no-op — send the full price shape together.
 */
@Schema(description = "Update a model on your AI provider (all fields optional)")
public record UpdateWorkspaceLlmModelRequestDTO(
    @Nullable @Size(max = 128) @Schema(description = "Human-readable name") String displayName,
    @Nullable @Size(max = 256) @Schema(description = "Upstream provider model id") String upstreamModelId,
    @Nullable
    @Size(max = 40)
    @Schema(description = "Wire protocol override for this model; blank clears the override")
    String apiProtocolOverride,
    @Nullable @Schema(description = "What surface this model serves") ModelModality modality,
    @Nullable @PositiveOrZero @Schema(description = "Context window in tokens") Integer contextWindow,
    @Nullable @PositiveOrZero @Schema(description = "Maximum output tokens") Integer maxOutputTokens,
    @Nullable @Schema(description = "Whether the model supports a reasoning mode") Boolean supportsReasoning,
    @Nullable
    @Size(max = 16)
    @Schema(description = "Cache-control wire format, if applicable; blank clears it")
    String cacheControlFormat,
    @Nullable @Schema(description = "Active toggle") Boolean enabled,
    @Nullable
    @Schema(description = "Pricing mode; when given, replaces the price wholesale (see class docs)")
    PricingMode pricingMode,
    @Nullable @Schema(description = "Input rate per 1M tokens (USD)") BigDecimal per1mInputUsd,
    @Nullable @Schema(description = "Output rate per 1M tokens (USD)") BigDecimal per1mOutputUsd,
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
