package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(
    description = "Request to update an existing agent configuration (all fields optional — null fields are not changed)"
)
public record UpdateAgentConfigRequestDTO(
    @Schema(description = "Whether the agent is enabled") Boolean enabled,
    @Size(max = 128, message = "Model name must not exceed 128 characters")
    @Schema(description = "LLM model name", example = "gpt-5.4-mini")
    String modelName,
    @Schema(description = "LLM API key (omit or null to keep existing key)") String llmApiKey,
    @Schema(description = "Set true to remove the stored API key (takes precedence over llmApiKey)")
    Boolean clearLlmApiKey,
    @Size(max = 512, message = "LLM base URL must not exceed 512 characters")
    @Schema(
        description = "Optional LLM base URL override (omit or null to keep existing value; " +
            "empty string clears it)"
    )
    String llmBaseUrl,
    @Schema(description = "LLM provider") LlmProvider llmProvider,
    @Min(value = 30, message = "Timeout must be at least 30 seconds")
    @Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    @Schema(description = "Job timeout in seconds", example = "600", minimum = "30", maximum = "3600")
    Integer timeoutSeconds,
    @Min(value = 1, message = "Max concurrent jobs must be at least 1")
    @Max(value = 10, message = "Max concurrent jobs must not exceed 10")
    @Schema(description = "Maximum concurrent jobs", example = "3", minimum = "1", maximum = "10")
    Integer maxConcurrentJobs,
    @Schema(description = "Whether agent containers have internet access") Boolean allowInternet,
    @Schema(description = "Authentication mode: PROXY (internal proxy) or API_KEY (direct)")
    CredentialMode credentialMode,
    @Schema(description = "Bind to a shared (instance catalog) model. Mutually exclusive with workspaceModelId.")
    Long instanceModelId,
    @Schema(description = "Bind to a model on your own provider. Mutually exclusive with instanceModelId.")
    Long workspaceModelId,
    @Schema(description = "Set true to clear the model binding (reverts to the legacy provider fields below)")
    Boolean clearModelBinding
) {}
