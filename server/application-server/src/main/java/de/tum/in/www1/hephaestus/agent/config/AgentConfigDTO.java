package de.tum.in.www1.hephaestus.agent.config;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.NonNull;

@Schema(description = "Agent configuration for a workspace (API key redacted)")
public record AgentConfigDTO(
    @NonNull @Schema(description = "Configuration ID") Long id,
    @NonNull @Schema(description = "Unique name within the workspace") String name,
    @NonNull @Schema(description = "Whether the agent is enabled") Boolean enabled,
    @NonNull @Schema(description = "Type of coding agent") AgentType agentType,
    @Schema(description = "LLM model name", example = "claude-sonnet-4-20250514") String modelName,
    @NonNull @Schema(description = "LLM provider") LlmProvider llmProvider,
    @NonNull @Schema(description = "Whether an LLM API key is configured") Boolean hasLlmApiKey,
    @NonNull @Schema(description = "Job timeout in seconds") Integer timeoutSeconds,
    @NonNull @Schema(description = "Maximum concurrent jobs") Integer maxConcurrentJobs,
    @NonNull @Schema(description = "Whether agent containers have internet access") Boolean allowInternet,
    @NonNull @Schema(description = "Authentication mode") CredentialMode credentialMode,
    @NonNull @Schema(description = "Timestamp when the config was created") Instant createdAt,
    @Schema(description = "Timestamp when the config was last updated") Instant updatedAt
) {
    public static AgentConfigDTO from(AgentConfig config) {
        return new AgentConfigDTO(
            config.getId(),
            config.getName(),
            config.isEnabled(),
            config.getAgentType(),
            config.getModelName(),
            config.getLlmProvider(),
            config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty(),
            config.getTimeoutSeconds(),
            config.getMaxConcurrentJobs(),
            config.isAllowInternet(),
            config.getCredentialMode(),
            config.getCreatedAt(),
            config.getUpdatedAt()
        );
    }
}
