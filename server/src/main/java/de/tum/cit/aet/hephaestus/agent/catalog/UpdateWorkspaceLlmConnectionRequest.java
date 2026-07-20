package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * Partial update of your own AI provider connection (#1368). Every field is optional; an absent (null)
 * field keeps its current value. The API key is write-only: an absent {@code apiKey} keeps the stored
 * key, and {@code clearApiKey=true} removes it (clearing wins over a supplied value).
 */
@Schema(description = "Update your AI provider connection (all fields optional)")
public record UpdateWorkspaceLlmConnectionRequest(
    @Nullable @Size(max = 128) @Schema(description = "Human-readable name") String displayName,
    @Nullable @Size(max = 2048) @Schema(description = "Provider base URL") String baseUrl,
    @Nullable
    @Pattern(
        regexp = "openai-completions|anthropic-messages|azure-openai-responses|openai-responses",
        message = "apiProtocol must be one of openai-completions, anthropic-messages, azure-openai-responses, openai-responses"
    )
    @Schema(description = "Wire protocol")
    String apiProtocol,
    @Nullable @Size(max = 64) @Schema(description = "Auth header name") String authHeaderName,
    @Nullable @Size(max = 16) @Schema(description = "Auth value prefix") String authValuePrefix,
    @Nullable @Schema(description = "New API key (write-only; never returned)") String apiKey,
    @Nullable @Schema(description = "Set true to clear the stored API key") Boolean clearApiKey,
    @Nullable @Size(max = 32) @Schema(description = "Azure API version") String azureApiVersion,
    @Nullable @Schema(description = "Whether the connection is active") Boolean enabled
) {}
