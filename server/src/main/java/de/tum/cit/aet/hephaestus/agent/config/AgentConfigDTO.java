package de.tum.cit.aet.hephaestus.agent.config;

import de.tum.cit.aet.hephaestus.agent.CredentialMode;
import de.tum.cit.aet.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.jspecify.annotations.NonNull;

@Schema(description = "Agent configuration for a workspace (API key redacted)")
public record AgentConfigDTO(
    @NonNull @Schema(description = "Configuration ID") Long id,
    @NonNull @Schema(description = "Unique name within the workspace") String name,
    @NonNull @Schema(description = "Whether the agent is enabled") Boolean enabled,
    @Schema(description = "LLM model name", example = "gpt-5.4-mini") String modelName,
    @NonNull @Schema(description = "LLM provider") LlmProvider llmProvider,
    @NonNull @Schema(description = "Whether an LLM API key is configured") Boolean hasLlmApiKey,
    @Schema(description = "Optional LLM base URL override") String llmBaseUrl,
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
            config.getModelName(),
            config.getLlmProvider(),
            config.getLlmApiKey() != null && !config.getLlmApiKey().isEmpty(),
            config.getLlmBaseUrl(),
            config.getTimeoutSeconds(),
            config.getMaxConcurrentJobs(),
            config.isAllowInternet(),
            config.getCredentialMode(),
            config.getCreatedAt(),
            config.getUpdatedAt()
        );
    }
}
