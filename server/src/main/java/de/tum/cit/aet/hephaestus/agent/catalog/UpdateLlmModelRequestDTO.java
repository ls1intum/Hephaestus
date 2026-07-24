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
    @Nullable @PositiveOrZero @Schema(description = "Context window in tokens") Integer contextWindow,
    @Nullable @PositiveOrZero @Schema(description = "Maximum output tokens") Integer maxOutputTokens,
    @Nullable @Schema(description = "Whether the model supports a reasoning mode") Boolean supportsReasoning,
    @Nullable @Schema(description = "Active toggle (off = existing settings stop working)") Boolean enabled
) {}
