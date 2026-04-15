package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;

@Schema(
    description = "Request to update an existing agent configuration (all fields optional — null fields are not changed)"
)
public record UpdateAgentConfigRequestDTO(
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(description = "Unique name within the workspace")
    String name,
    @Schema(description = "Whether the review agent is enabled") Boolean enabled,
    @Schema(description = "Runner that executes this agent") Long runnerId,
    @Schema(description = "Type of coding agent") AgentType agentType,
    @Size(max = 128, message = "Model name must not exceed 128 characters")
    @Schema(description = "LLM model name", example = "claude-sonnet-4-20250514")
    String modelName,
    @Schema(description = "Clear the stored model name") Boolean clearModelName,
    @Size(max = 50, message = "Model version must not exceed 50 characters")
    @Schema(description = "Model version or snapshot date", example = "2026-03-17")
    String modelVersion,
    @Schema(description = "Clear the stored model version") Boolean clearModelVersion,
    @Schema(description = "LLM API key (omit or null to keep existing key)") String llmApiKey,
    @Schema(description = "Clear the stored LLM API key") Boolean clearLlmApiKey,
    @Schema(description = "LLM provider") LlmProvider llmProvider,
    @Min(value = 30, message = "Timeout must be at least 30 seconds")
    @Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    @Schema(description = "Job timeout in seconds", example = "600", minimum = "30", maximum = "3600")
    Integer timeoutSeconds,
    @Min(value = 1, message = "Max concurrent jobs must be at least 1")
    @Max(value = 10, message = "Max concurrent jobs must not exceed 10")
    @Schema(description = "Maximum concurrent jobs", example = "3", minimum = "1", maximum = "10")
    Integer maxConcurrentJobs,
    @Schema(description = "Whether runner containers have internet access") Boolean allowInternet,
    @Schema(description = "Authentication mode: PROXY (internal proxy), API_KEY (direct), or OAUTH (direct OAuth)")
    CredentialMode credentialMode
) {
    public UpdateAgentConfigRequestDTO(
        String name,
        Boolean enabled,
        AgentType agentType,
        String modelName,
        Boolean clearModelName,
        String modelVersion,
        Boolean clearModelVersion,
        String llmApiKey,
        Boolean clearLlmApiKey,
        LlmProvider llmProvider,
        Integer timeoutSeconds,
        Integer maxConcurrentJobs,
        Boolean allowInternet,
        CredentialMode credentialMode
    ) {
        this(
            name,
            enabled,
            null,
            agentType,
            modelName,
            clearModelName,
            modelVersion,
            clearModelVersion,
            llmApiKey,
            clearLlmApiKey,
            llmProvider,
            timeoutSeconds,
            maxConcurrentJobs,
            allowInternet,
            credentialMode
        );
    }
}
