package de.tum.in.www1.hephaestus.agent.runner;

import de.tum.in.www1.hephaestus.agent.AgentType;
import de.tum.in.www1.hephaestus.agent.CredentialMode;
import de.tum.in.www1.hephaestus.agent.LlmProvider;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import org.springframework.lang.NonNull;

@Schema(description = "Reusable runner for executing workspace review agents (API key redacted)")
public record AgentRunnerDTO(
    @NonNull @Schema(description = "Runner ID") Long id,
    @NonNull @Schema(description = "Unique name within the workspace") String name,
    @NonNull @Schema(description = "Type of coding agent") AgentType agentType,
    @Schema(description = "LLM model name", example = "claude-sonnet-4-20250514") String modelName,
    @Schema(description = "Model version or snapshot date", example = "2026-03-17") String modelVersion,
    @NonNull @Schema(description = "LLM provider") LlmProvider llmProvider,
    @NonNull @Schema(description = "Whether an LLM API key is configured") Boolean hasLlmApiKey,
    @NonNull @Schema(description = "Job timeout in seconds") Integer timeoutSeconds,
    @NonNull @Schema(description = "Maximum concurrent jobs") Integer maxConcurrentJobs,
    @NonNull @Schema(description = "Whether runner containers have internet access") Boolean allowInternet,
    @NonNull @Schema(description = "Authentication mode") CredentialMode credentialMode,
    @NonNull @Schema(description = "Timestamp when the runner was created") Instant createdAt,
    @Schema(description = "Timestamp when the runner was last updated") Instant updatedAt
) {
    public static AgentRunnerDTO from(AgentRunner runner) {
        return new AgentRunnerDTO(
            runner.getId(),
            runner.getName(),
            runner.getAgentType(),
            runner.getModelName(),
            runner.getModelVersion(),
            runner.getLlmProvider(),
            runner.getLlmApiKey() != null && !runner.getLlmApiKey().isEmpty(),
            runner.getTimeoutSeconds(),
            runner.getMaxConcurrentJobs(),
            runner.isAllowInternet(),
            runner.getCredentialMode(),
            runner.getCreatedAt(),
            runner.getUpdatedAt()
        );
    }
}
