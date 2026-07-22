package de.tum.cit.aet.hephaestus.agent.config;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Builder;

@Builder
@Schema(description = "Request to create a new agent configuration for a workspace")
public record CreateAgentConfigRequestDTO(
    @NotBlank(message = "Name is required")
    @Size(max = 100, message = "Name must not exceed 100 characters")
    @Schema(description = "Unique name within the workspace", example = "pi-pr-reviewer")
    String name,
    @Schema(description = "Whether the agent is enabled") Boolean enabled,
    @Min(value = 30, message = "Timeout must be at least 30 seconds")
    @Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    @Schema(description = "Job timeout in seconds", example = "600", minimum = "30", maximum = "3600")
    Integer timeoutSeconds,
    @Min(value = 1, message = "Max concurrent jobs must be at least 1")
    @Max(value = 10, message = "Max concurrent jobs must not exceed 10")
    @Schema(description = "Maximum concurrent jobs", example = "3", minimum = "1", maximum = "10")
    Integer maxConcurrentJobs,
    @Schema(description = "Whether agent containers have internet access") Boolean allowInternet,
    @Schema(description = "Bind to a shared (instance catalog) model. Mutually exclusive with workspaceModelId.")
    Long instanceModelId,
    @Schema(description = "Bind to a model on your own provider. Mutually exclusive with instanceModelId.")
    Long workspaceModelId
) {}
