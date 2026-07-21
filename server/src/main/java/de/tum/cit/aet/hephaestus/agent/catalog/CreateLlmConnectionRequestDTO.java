package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Create an instance LLM connection (#1368). */
@Schema(description = "Create an instance LLM provider connection")
public record CreateLlmConnectionRequestDTO(
    @NonNull
    @NotBlank
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{0,62}$",
        message = "slug must be lowercase alphanumeric with hyphens (max 63 chars)"
    )
    @Schema(description = "Unique slug", example = "openai-prod")
    String slug,
    @NonNull @NotBlank @Size(max = 128) @Schema(description = "Human-readable name") String displayName,
    @NonNull @NotBlank @Size(max = 2048) @Schema(description = "Provider base URL") String baseUrl,
    @NonNull
    @NotBlank
    @Pattern(
        regexp = "openai-completions|anthropic-messages|azure-openai-responses|openai-responses",
        message = "apiProtocol must be one of openai-completions, anthropic-messages, azure-openai-responses, openai-responses"
    )
    @Schema(description = "Wire protocol", example = "openai-completions")
    String apiProtocol,
    @Nullable @Size(max = 64) @Schema(description = "Auth header name; defaults from protocol") String authHeaderName,
    @Nullable @Size(max = 16) @Schema(description = "Auth value prefix; defaults from protocol") String authValuePrefix,
    @Nullable @Schema(description = "API key (write-only; never returned)") String apiKey,
    @Nullable @Size(max = 32) @Schema(description = "Azure API version, if applicable") String azureApiVersion,
    @Nullable @Schema(description = "Whether the connection is enabled (default true)") Boolean enabled
) {}
