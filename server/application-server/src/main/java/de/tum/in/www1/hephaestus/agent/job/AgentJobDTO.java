package de.tum.in.www1.hephaestus.agent.job;

import com.fasterxml.jackson.databind.ObjectMapper;
import de.tum.in.www1.hephaestus.agent.AgentJobType;
import de.tum.in.www1.hephaestus.agent.config.ConfigSnapshot;
import io.swagger.v3.oas.annotations.media.Schema;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;

@Schema(description = "Agent job execution record (job_token intentionally omitted)")
public record AgentJobDTO(
    @NonNull @Schema(description = "Job ID") UUID id,
    @NonNull @Schema(description = "Job type") AgentJobType jobType,
    @NonNull @Schema(description = "Current job status") AgentJobStatus status,
    @Schema(description = "Job metadata (routing/display info)") Object metadata,
    @Schema(description = "Job output (agent results)") Object output,
    @NonNull @Schema(description = "Frozen agent config at submit time") Object configSnapshot,
    @Schema(description = "Frozen config name at submit time") String configName,
    @Schema(description = "Frozen agent type at submit time") String configAgentType,
    @Schema(description = "Frozen provider at submit time") String configLlmProvider,
    @Schema(description = "Frozen model name at submit time") String configModelName,
    @Schema(description = "Frozen model version at submit time") String configModelVersion,
    @Schema(description = "Docker container ID") String containerId,
    @Schema(description = "Container exit code") Integer exitCode,
    @Schema(description = "Human-readable error message") String errorMessage,
    @Schema(
        description = "Delivery status: null = not applicable, PENDING = awaiting delivery, DELIVERED = posted, FAILED = delivery error"
    )
    DeliveryStatus deliveryStatus,
    @Schema(description = "Git provider comment/note ID for posted feedback") String deliveryCommentId,
    @NonNull @Schema(description = "Number of retry attempts") Integer retryCount,
    @NonNull @Schema(description = "Timestamp when the job was created") Instant createdAt,
    @Schema(description = "Timestamp when the job started running") Instant startedAt,
    @Schema(description = "Timestamp when the job completed") Instant completedAt,
    @Schema(description = "LLM model used (e.g. gpt-5.4-mini, claude-sonnet-4-5)") String llmModel,
    @Schema(description = "Model version/snapshot date (e.g. 2026-03-17)") String llmModelVersion,
    @Schema(description = "Total LLM API calls (steps) during execution") Integer llmTotalCalls,
    @Schema(description = "Total input tokens consumed") Integer llmTotalInputTokens,
    @Schema(description = "Total output tokens generated") Integer llmTotalOutputTokens,
    @Schema(description = "Total reasoning/thinking tokens") Integer llmTotalReasoningTokens,
    @Schema(description = "Tokens read from prompt cache") Integer llmCacheReadTokens,
    @Schema(description = "Tokens written to prompt cache") Integer llmCacheWriteTokens,
    @Schema(description = "Estimated cost in USD (agent-reported)") Double llmCostUsd
) {
    private static final Logger log = LoggerFactory.getLogger(AgentJobDTO.class);

    public static AgentJobDTO from(AgentJob job, ObjectMapper objectMapper) {
        ConfigSnapshot snapshot = null;
        try {
            if (job.getConfigSnapshot() != null) {
                snapshot = ConfigSnapshot.fromJson(job.getConfigSnapshot(), objectMapper);
            }
        } catch (Exception exception) {
            log.warn("Failed to parse config snapshot for agent job {}", job.getId(), exception);
            snapshot = null;
        }

        return new AgentJobDTO(
            job.getId(),
            job.getJobType(),
            job.getStatus(),
            job.getMetadata(),
            job.getOutput(),
            job.getConfigSnapshot(),
            snapshot != null ? snapshot.configName() : null,
            snapshot != null ? snapshot.agentType().name() : null,
            snapshot != null ? snapshot.llmProvider().name() : null,
            snapshot != null ? snapshot.modelName() : null,
            snapshot != null ? snapshot.modelVersion() : null,
            job.getContainerId(),
            job.getExitCode(),
            job.getErrorMessage(),
            job.getDeliveryStatus(),
            job.getDeliveryCommentId(),
            job.getRetryCount(),
            job.getCreatedAt(),
            job.getStartedAt(),
            job.getCompletedAt(),
            job.getLlmModel(),
            job.getLlmModelVersion(),
            job.getLlmTotalCalls(),
            job.getLlmTotalInputTokens(),
            job.getLlmTotalOutputTokens(),
            job.getLlmTotalReasoningTokens(),
            job.getLlmCacheReadTokens(),
            job.getLlmCacheWriteTokens(),
            job.getLlmCostUsd()
        );
    }
}
