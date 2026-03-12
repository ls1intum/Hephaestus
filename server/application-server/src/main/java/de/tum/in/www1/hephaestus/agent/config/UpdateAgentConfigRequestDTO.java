package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

@Schema(description = "Request to create or update the agent configuration for a workspace")
public record UpdateAgentConfigRequestDTO(
    @Schema(description = "Whether the agent is enabled") Boolean enabled,
    @NotNull(message = "Agent type is required")
    @Schema(description = "Type of coding agent", requiredMode = Schema.RequiredMode.REQUIRED)
    AgentType agentType,
    @Schema(description = "LLM model name", example = "claude-sonnet-4-20250514") String modelName,
    @Schema(description = "LLM API key (omit or null to keep existing key)") String llmApiKey,
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
