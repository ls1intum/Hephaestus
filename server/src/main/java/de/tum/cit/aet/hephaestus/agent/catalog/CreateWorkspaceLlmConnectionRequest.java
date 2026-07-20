package de.tum.cit.aet.hephaestus.agent.catalog;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.NonNull;
import org.jspecify.annotations.Nullable;

/** Connect your own AI provider (#1368): a workspace-owned, tenant-scoped LLM connection. */
@Schema(description = "Connect your own AI provider")
public record CreateWorkspaceLlmConnectionRequest(
    @NonNull
    @NotBlank
    @Pattern(
        regexp = "^[a-z0-9][a-z0-9-]{0,62}$",
        message = "slug must be lowercase alphanumeric with hyphens (max 63 chars)"
    )
    @Schema(description = "Unique slug within the workspace", example = "openai-prod")
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
    @Nullable @Schema(description = "Whether the connection is active (default true)") Boolean enabled
) {}
