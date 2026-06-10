package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
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
    @Size(max = 128, message = "Model name must not exceed 128 characters")
    @Schema(description = "LLM model name", example = "gpt-5.4-mini")
    String modelName,
    @Schema(description = "LLM API key") String llmApiKey,
    @Size(max = 512, message = "LLM base URL must not exceed 512 characters")
    @Schema(
        description = "Optional LLM base URL — set for OpenAI/Anthropic-compatible endpoints " +
            "that need routing through the hephaestus chat/completions provider extension " +
            "(e.g. TUM GPU, on-prem gateways)",
        example = "https://gpu.example.com"
    )
    String llmBaseUrl,
    @NotNull(message = "LLM provider is required") @Schema(description = "LLM provider") LlmProvider llmProvider,
    @Min(value = 30, message = "Timeout must be at least 30 seconds")
    @Max(value = 3600, message = "Timeout must not exceed 3600 seconds")
    @Schema(description = "Job timeout in seconds", example = "600", minimum = "30", maximum = "3600")
    Integer timeoutSeconds,
    @Min(value = 1, message = "Max concurrent jobs must be at least 1")
    @Max(value = 10, message = "Max concurrent jobs must not exceed 10")
    @Schema(description = "Maximum concurrent jobs", example = "3", minimum = "1", maximum = "10")
    Integer maxConcurrentJobs,
    @Schema(description = "Whether agent containers have internet access") Boolean allowInternet,
    @Schema(description = "Authentication mode: PROXY (internal proxy) or API_KEY (direct)", defaultValue = "PROXY")
    CredentialMode credentialMode
) {}
