package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Create a model on an instance LLM connection (#1368). Pricing and sharing are set separately. */
@Schema(description = "Create a model on an instance LLM connection")
public record CreateLlmModelRequestDTO(
    @Nullable
    @Size(max = 63)
    @Schema(description = "Optional internal slug; generated from displayName when omitted")
    String slug,
    @NonNull @NotBlank @Size(max = 128) @Schema(description = "Human-readable name") String displayName,
    @NonNull @NotBlank @Size(max = 256) @Schema(description = "Upstream provider model id") String upstreamModelId,
    @Nullable @PositiveOrZero @Schema(description = "Context window in tokens") Integer contextWindow,
    @Nullable @PositiveOrZero @Schema(description = "Maximum output tokens") Integer maxOutputTokens,
    @Nullable @Schema(description = "Whether the model supports a reasoning mode") Boolean supportsReasoning,
    @Nullable @Schema(description = "Whether the model is active (default false)") Boolean enabled
) {}
