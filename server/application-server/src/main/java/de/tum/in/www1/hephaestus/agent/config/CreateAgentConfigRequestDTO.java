package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to create a new review-agent configuration for a workspace")
public record CreateAgentConfigRequestDTO(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(
        description = "Unique name within the workspace",
        example = "claude-pr-reviewer",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String name,
    @Schema(description = "Whether the review agent is enabled") Boolean enabled,
    @Schema(description = "Existing runner that executes this agent") Long runnerId,
    @Schema(description = "Type of coding agent") AgentType agentType,
    @Size(max = 128, message = "Model name must not exceed 128 characters")
    @Schema(description = "LLM model name", example = "claude-sonnet-4-20250514")
    String modelName,
    @Size(max = 50, message = "Model version must not exceed 50 characters")
    @Schema(description = "Model version or snapshot date", example = "2026-03-17")
    String modelVersion,
    @Schema(description = "LLM API key") String llmApiKey,
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
    public CreateAgentConfigRequestDTO(
        String name,
        Boolean enabled,
        AgentType agentType,
        String modelName,
        String modelVersion,
        String llmApiKey,
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
            modelVersion,
            llmApiKey,
            llmProvider,
            timeoutSeconds,
            maxConcurrentJobs,
            allowInternet,
            credentialMode
        );
    }
}
