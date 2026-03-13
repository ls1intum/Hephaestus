package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Schema(description = "Request to create a new agent configuration for a workspace")
public record CreateAgentConfigRequestDTO(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(
        description = "Unique name within the workspace",
        example = "claude-pr-reviewer",
        requiredMode = Schema.RequiredMode.REQUIRED
    )
    String name,
    @Schema(description = "Whether the agent is enabled") Boolean enabled,
    @NotNull(message = "Agent type is required")
    @Schema(description = "Type of coding agent", requiredMode = Schema.RequiredMode.REQUIRED)
    AgentType agentType,
    @Schema(description = "LLM model name", example = "claude-sonnet-4-20250514") String modelName,
    @Schema(description = "LLM API key") String llmApiKey,
    @NotNull(message = "LLM provider is required")
    @Schema(description = "LLM provider", requiredMode = Schema.RequiredMode.REQUIRED)
    LlmProvider llmProvider,
    @Min(value = 30, message = "Timeout must be at least 30 seconds")
    @Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    @Schema(description = "Job timeout in seconds", example = "600", minimum = "30", maximum = "3600")
    Integer timeoutSeconds,
    @Min(value = 1, message = "Max concurrent jobs must be at least 1")
    @Max(value = 10, message = "Max concurrent jobs must not exceed 10")
    @Schema(description = "Maximum concurrent jobs", example = "3", minimum = "1", maximum = "10")
    Integer maxConcurrentJobs,
    @Schema(description = "Whether agent containers have internet access") Boolean allowInternet
) {}
