package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Partial update of a model's metadata (#1368). Every field is optional; an absent (null) field keeps
 * its current value. Pricing and sharing are updated through their own endpoints, not here.
 */
@Schema(description = "Update a model's metadata (all fields optional; pricing and sharing are separate)")
public record UpdateLlmModelRequestDTO(
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
    @Nullable @Schema(description = "Active toggle (off = existing settings stop working)") Boolean enabled
) {}
