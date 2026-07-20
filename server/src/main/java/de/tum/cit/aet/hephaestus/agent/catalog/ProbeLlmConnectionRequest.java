package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/**
 * Draft probe request (#1368): test an as-yet-unsaved connection against its {@code /models} endpoint
 * using the supplied credential. The credential is used only for the probe and never persisted.
 */
@Schema(description = "Draft connection probe using a supplied (never-persisted) credential")
public record ProbeLlmConnectionRequest(
    @NonNull @NotBlank @Schema(description = "Provider base URL") String baseUrl,
    @NonNull
    @NotBlank
    @Pattern(
        regexp = "openai-completions|anthropic-messages|azure-openai-responses|openai-responses",
        message = "apiProtocol must be one of openai-completions, anthropic-messages, azure-openai-responses, openai-responses"
    )
    @Schema(description = "Wire protocol")
    String apiProtocol,
    @Nullable @Schema(description = "Auth header name; defaults from protocol") String authHeaderName,
    @Nullable @Schema(description = "Auth value prefix; defaults from protocol") String authValuePrefix,
    @Nullable @Schema(description = "API key used only for this probe") String apiKey
) {}
